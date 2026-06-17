package com.laulain.rentals.service;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.AvailabilityDto.AvailabilityCheckRequest;
import com.laulain.rentals.dto.BookingDto.*;
import com.laulain.rentals.exception.BookingConflictException;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    private static final int DEPOSIT_PERCENTAGE = 30;

    // ============================================================
    //  CREATE BOOKING
    // ============================================================

    @Transactional
    public BookingResponse createBooking(BookingRequest request, Object ignoredPrincipal) {

        // 1. Resolve or create customer
        Customer customer = resolveCustomer(request);

        // 2. Load and validate all requested items
        List<ItemWithQty> itemsWithQty = resolveItems(request.getItems());

        // 3. Check availability for every item on the event date
        validateAvailability(itemsWithQty, request.getEventDate());

        // 4. Calculate pricing
        PricingEstimate pricing = calculatePricing(itemsWithQty, 1, request.getRequiresSetup());

        // 5. Build booking entity
        String bookingNumber = generateBookingNumber();
        Booking booking = Booking.builder()
                .bookingNumber(bookingNumber)
                .customer(customer)
                .status(Booking.Status.PENDING)
                .eventDate(request.getEventDate())
                .eventStartTime(request.getEventStartTime())
                .eventEndTime(request.getEventEndTime())
                .eventType(request.getEventType())
                .eventVenue(request.getEventVenue())
                .guestCount(request.getGuestCount())
                .deliveryAddressLine1(request.getDeliveryAddressLine1())
                .deliveryAddressLine2(request.getDeliveryAddressLine2())
                .deliveryCity(request.getDeliveryCity())
                .deliveryState(request.getDeliveryState())
                .deliveryZip(request.getDeliveryZip())
                .deliveryFee(pricing.getDeliveryFee())
                .requiresSetup(request.getRequiresSetup() != null && request.getRequiresSetup())
                .setupFee(pricing.getSetupFee())
                .subtotal(pricing.getSubtotal())
                .taxAmount(pricing.getTaxAmount())
                .totalAmount(pricing.getTotalAmount())
                .depositAmount(pricing.getDepositAmount())
                .balanceDue(pricing.getBalanceDue())
                .customerNotes(request.getCustomerNotes())
                .build();

        // 6. Add line items
        for (ItemWithQty iwq : itemsWithQty) {
            BigDecimal lineTotal = iwq.item().getPricePerDay()
                    .multiply(BigDecimal.valueOf(iwq.quantity()));
            BookingItem lineItem = BookingItem.builder()
                    .booking(booking)
                    .item(iwq.item())
                    .itemName(iwq.item().getName())
                    .quantity(iwq.quantity())
                    .unitPrice(iwq.item().getPricePerDay())
                    .lineTotal(lineTotal)
                    .rentalDays(1)
                    .build();
            booking.getBookingItems().add(lineItem);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Created booking {} for customer {}", bookingNumber, customer.getEmail());

        // 7. Fire notifications asynchronously
        notificationService.sendBookingConfirmation(saved);
        notificationService.sendAdminNewBookingAlert(saved);

        return toBookingResponse(saved);
    }

    // ============================================================
    //  PRICING ESTIMATE
    // ============================================================

    public PricingEstimate calculatePricingEstimate(List<BookingItemRequest> itemRequests,
                                                     int rentalDays, boolean requiresSetup) {
        List<ItemWithQty> items = resolveItems(itemRequests);
        return calculatePricing(items, rentalDays, requiresSetup);
    }

    // ============================================================
    //  STATUS TRANSITIONS
    // ============================================================

    @Transactional
    public BookingResponse confirmBooking(UUID bookingId) {
        Booking booking = getBookingEntity(bookingId);

        if (booking.getStatus() == Booking.Status.CONFIRMED) {
            return toBookingResponse(booking);
        }

        booking.setStatus(Booking.Status.CONFIRMED);
        booking.setConfirmedAt(Instant.now());
        bookingRepository.save(booking);

        availabilityService.createBlocksForBooking(booking);
        notificationService.sendBookingConfirmedNotification(booking);
        log.info("Confirmed booking {}", booking.getBookingNumber());

        return toBookingResponse(booking);
    }

    @Transactional
    public void confirmBookingByPayment(UUID bookingId) {
        confirmBooking(bookingId);
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, String reason) {
        Booking booking = getBookingEntity(bookingId);

        if (!booking.isCancellable()) {
            throw new IllegalStateException("Booking " + booking.getBookingNumber() + " cannot be cancelled in its current status.");
        }

        // Capture confirmed state BEFORE mutating status
        boolean wasConfirmed = booking.isConfirmed();

        booking.setStatus(Booking.Status.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancelledAt(Instant.now());
        bookingRepository.save(booking);

        if (wasConfirmed) {
            availabilityService.releaseBlocksForBooking(bookingId);
        }

        notificationService.sendCancellationNotification(booking);
        log.info("Cancelled booking {}", booking.getBookingNumber());

        return toBookingResponse(booking);
    }

    @Transactional
    public BookingResponse updateAdminNotes(UUID bookingId, AdminBookingUpdate update) {
        Booking booking = getBookingEntity(bookingId);
        if (update.getAdminNotes() != null) booking.setAdminNotes(update.getAdminNotes());
        if (update.getStatus() != null)     booking.setStatus(update.getStatus());
        return toBookingResponse(bookingRepository.save(booking));
    }

    // ============================================================
    //  QUERIES
    // ============================================================

    public BookingResponse getBookingById(UUID id) {
        return toBookingResponse(getBookingEntityWithDetails(id));
    }

    public BookingResponse getBookingByNumber(String bookingNumber) {
        Booking booking = bookingRepository.findByBookingNumber(bookingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingNumber));
        return toBookingResponse(booking);
    }

    public Page<BookingSummary> getBookingsForAdmin(Booking.Status status, LocalDate eventDate,
                                                     String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return bookingRepository.findForAdmin(status, eventDate, search, pageable)
                .map(this::toBookingSummary);
    }

    public Page<BookingSummary> getBookingsForCustomer(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toBookingSummary);
    }

    // ============================================================
    //  SCHEDULED
    // ============================================================

    public void sendRemindersForDate(LocalDate targetDate) {
        List<Booking> bookings = bookingRepository.findConfirmedBookingsOnDate(targetDate);
        bookings.forEach(booking -> {
            notificationService.sendEventReminder(booking);
            log.info("Sent reminder for booking {}", booking.getBookingNumber());
        });
    }

    // ============================================================
    //  PRIVATE HELPERS
    // ============================================================

    private Customer resolveCustomer(BookingRequest request) {
        // Look up by email
        Optional<Customer> byEmail = customerRepository.findByEmail(request.getEmail());
        if (byEmail.isPresent()) {
            Customer c = byEmail.get();
            c.setFirstName(request.getFirstName());
            c.setLastName(request.getLastName());
            c.setPhone(request.getPhone());
            return customerRepository.save(c);
        }

        // Create new customer
        Customer newCustomer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .addressLine1(request.getDeliveryAddressLine1())
                .city(request.getDeliveryCity())
                .state(request.getDeliveryState())
                .zipCode(request.getDeliveryZip())
                .build();

        return customerRepository.save(newCustomer);
    }

    private List<ItemWithQty> resolveItems(List<BookingItemRequest> requests) {
        return requests.stream().map(req -> {
            Item item = itemRepository.findById(req.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + req.getItemId()));
            if (!item.getActive()) {
                throw new ResourceNotFoundException("Item is no longer available: " + item.getName());
            }
            return new ItemWithQty(item, req.getQuantity());
        }).collect(Collectors.toList());
    }

    private void validateAvailability(List<ItemWithQty> items, LocalDate eventDate) {
        List<String> conflicts = new ArrayList<>();

        for (ItemWithQty iwq : items) {
            var checkResult = availabilityService.checkAvailability(
                    new AvailabilityCheckRequest(
                            iwq.item().getId(), eventDate, eventDate, iwq.quantity()));

            if (!checkResult.isAvailable()) {
                conflicts.add(iwq.item().getName() + ": " + checkResult.getMessage());
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException("Availability conflict:\n" + String.join("\n", conflicts));
        }
    }

    private PricingEstimate calculatePricing(List<ItemWithQty> items, int rentalDays,
                                              Boolean requiresSetup) {
        AppProperties.BusinessProperties biz = appProperties.business();

        List<LineItemEstimate> lineItems = items.stream().map(iwq -> {
            BigDecimal lineTotal = iwq.item().getPricePerDay()
                    .multiply(BigDecimal.valueOf(iwq.quantity()))
                    .multiply(BigDecimal.valueOf(rentalDays));
            return LineItemEstimate.builder()
                    .itemId(iwq.item().getId())
                    .itemName(iwq.item().getName())
                    .quantity(iwq.quantity())
                    .unitPrice(iwq.item().getPricePerDay())
                    .rentalDays(rentalDays)
                    .lineTotal(lineTotal)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal subtotal = lineItems.stream()
                .map(LineItemEstimate::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal setupFee = (requiresSetup != null && requiresSetup)
                ? subtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal taxRate = BigDecimal.valueOf(biz.taxRate());
        BigDecimal taxAmount = subtotal.add(deliveryFee).add(setupFee)
                .multiply(taxRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = subtotal.add(deliveryFee).add(setupFee).add(taxAmount);

        BigDecimal depositPct = BigDecimal.valueOf(DEPOSIT_PERCENTAGE)
                .divide(BigDecimal.valueOf(100));
        BigDecimal depositAmount = totalAmount.multiply(depositPct)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal balanceDue = totalAmount.subtract(depositAmount);

        return PricingEstimate.builder()
                .lineItems(lineItems)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .setupFee(setupFee)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .depositAmount(depositAmount)
                .balanceDue(balanceDue)
                .taxRate(biz.taxRate() * 100)
                .depositPercentage(DEPOSIT_PERCENTAGE)
                .build();
    }

    private String generateBookingNumber() {
        long seq = bookingRepository.nextBookingSequence();
        return String.format("LLR-%d-%04d", Year.now().getValue(), seq);
    }

    private Booking getBookingEntity(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    private Booking getBookingEntityWithDetails(UUID id) {
        return bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    // ---- Mapping helpers ----

    public BookingResponse toBookingResponse(Booking b) {
        String deliveryAddress = buildDeliveryAddress(b);

        List<BookingLineItemResponse> lineItems = b.getBookingItems().stream()
                .map(li -> BookingLineItemResponse.builder()
                        .id(li.getId())
                        .itemId(li.getItem() != null ? li.getItem().getId() : null)
                        .itemName(li.getItemName())
                        .quantity(li.getQuantity())
                        .unitPrice(li.getUnitPrice())
                        .lineTotal(li.getLineTotal())
                        .rentalDays(li.getRentalDays())
                        .build())
                .collect(Collectors.toList());

        return BookingResponse.builder()
                .id(b.getId())
                .bookingNumber(b.getBookingNumber())
                .status(b.getStatus())
                .statusLabel(statusLabel(b.getStatus()))
                .statusColor(statusColor(b.getStatus()))
                .customerName(b.getCustomer() != null ? b.getCustomer().getFullName() : "")
                .customerEmail(b.getCustomer() != null ? b.getCustomer().getEmail() : "")
                .customerPhone(b.getCustomer() != null ? b.getCustomer().getPhone() : "")
                .eventDate(b.getEventDate())
                .eventStartTime(b.getEventStartTime())
                .eventType(b.getEventType())
                .eventVenue(b.getEventVenue())
                .guestCount(b.getGuestCount())
                .deliveryAddress(deliveryAddress)
                .deliveryFee(b.getDeliveryFee())
                .requiresSetup(b.getRequiresSetup())
                .setupFee(b.getSetupFee())
                .subtotal(b.getSubtotal())
                .taxAmount(b.getTaxAmount())
                .totalAmount(b.getTotalAmount())
                .depositAmount(b.getDepositAmount())
                .balanceDue(b.getBalanceDue())
                .items(lineItems)
                .customerNotes(b.getCustomerNotes())
                .adminNotes(b.getAdminNotes())
                .createdAt(b.getCreatedAt())
                .confirmedAt(b.getConfirmedAt())
                .build();
    }

    private BookingSummary toBookingSummary(Booking b) {
        return BookingSummary.builder()
                .id(b.getId())
                .bookingNumber(b.getBookingNumber())
                .customerName(b.getCustomer() != null ? b.getCustomer().getFullName() : "")
                .eventDate(b.getEventDate())
                .eventType(b.getEventType())
                .status(b.getStatus())
                .statusLabel(statusLabel(b.getStatus()))
                .statusColor(statusColor(b.getStatus()))
                .totalAmount(b.getTotalAmount())
                .depositAmount(b.getDepositAmount())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private String buildDeliveryAddress(Booking b) {
        StringBuilder sb = new StringBuilder();
        if (b.getDeliveryAddressLine1() != null) sb.append(b.getDeliveryAddressLine1());
        if (b.getDeliveryAddressLine2() != null) sb.append(", ").append(b.getDeliveryAddressLine2());
        if (b.getDeliveryCity() != null) sb.append(", ").append(b.getDeliveryCity());
        if (b.getDeliveryState() != null) sb.append(", ").append(b.getDeliveryState());
        if (b.getDeliveryZip() != null) sb.append(" ").append(b.getDeliveryZip());
        return sb.toString();
    }

    private String statusLabel(Booking.Status status) {
        return switch (status) {
            case PENDING     -> "Pending Review";
            case QUOTE_SENT  -> "Quote Sent";
            case CONFIRMED   -> "Confirmed";
            case IN_PROGRESS -> "In Progress";
            case COMPLETED   -> "Completed";
            case CANCELLED   -> "Cancelled";
            case NO_SHOW     -> "No Show";
        };
    }

    private String statusColor(Booking.Status status) {
        return switch (status) {
            case PENDING     -> "gray";
            case QUOTE_SENT  -> "blue";
            case CONFIRMED   -> "gold";
            case IN_PROGRESS -> "green";
            case COMPLETED   -> "purple";
            case CANCELLED   -> "red";
            case NO_SHOW     -> "red";
        };
    }

    private record ItemWithQty(Item item, int quantity) {}
}
