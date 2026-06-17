package com.laulain.rentals.service;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.AvailabilityDto.*;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.AvailabilityBlock;
import com.laulain.rentals.model.Booking;
import com.laulain.rentals.model.Item;
import com.laulain.rentals.repository.AvailabilityBlockRepository;
import com.laulain.rentals.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core availability engine for Laulain Luxe Rentals.
 *
 * Key rules:
 *  1. Each item has a quantityInStock — multiple bookings can share an item on the same date
 *     as long as total blocked quantity < stock.
 *  2. Buffer days: 1 day before and after each confirmed booking is auto-blocked.
 *  3. Minimum advance booking: customers must book at least N days ahead.
 *  4. Availability blocks are only created when a booking is CONFIRMED (deposit paid).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AvailabilityService {

    private final AvailabilityBlockRepository blockRepository;
    private final ItemRepository itemRepository;
    private final AppProperties appProperties;

    // ============================================================
    //  PUBLIC — Check availability for a specific item + date range
    // ============================================================

    /**
     * Check if an item is available for the requested dates and quantity.
     * This is the primary method called from the booking form.
     */
    public AvailabilityCheckResponse checkAvailability(AvailabilityCheckRequest request) {
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + request.getItemId()));

        return checkItemAvailability(item, request.getStartDate(), request.getEndDate(),
                request.getQuantity() != null ? request.getQuantity() : 1, null);
    }

    /**
     * Check availability for multiple items at once — used when building a full booking cart.
     */
    public List<AvailabilityCheckResponse> checkMultipleItems(
            List<AvailabilityCheckRequest> requests) {
        return requests.stream()
                .map(this::checkAvailability)
                .collect(Collectors.toList());
    }

    /**
     * Check if ALL items in a booking are available.
     * Excludes the booking itself (for re-checking after edits).
     */
    public boolean isBookingAvailable(Booking booking, UUID excludeBookingId) {
        LocalDate start = booking.getEventDate();
        LocalDate end = booking.getEventDate();  // Single day — extend for multi-day rentals

        for (var lineItem : booking.getBookingItems()) {
            if (lineItem.getItem() == null) continue;
            Item item = lineItem.getItem();

            long unavailableDays = blockRepository.countUnavailableDates(
                    item.getId(), start, end, item.getQuantityInStock(), excludeBookingId);

            if (unavailableDays > 0) return false;
        }
        return true;
    }

    // ============================================================
    //  PUBLIC — Monthly calendar data for a specific item
    // ============================================================

    /**
     * Returns day-by-day availability status for a specific item for a given month.
     * Used by the public-facing FullCalendar on the item detail page.
     */
    @Cacheable(value = "item-calendar", key = "#itemId + '_' + #year + '_' + #month")
    public MonthlyAvailability getMonthlyAvailability(UUID itemId, int year, int month) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        LocalDate today = LocalDate.now();
        int minAdvanceDays = appProperties.business().minBookingDaysAhead();

        // Fetch all blocks for this item in this month + buffer window
        List<AvailabilityBlock> blocks = blockRepository.findBlocksForItem(
                itemId,
                startOfMonth.minusDays(appProperties.business().bufferDays()),
                endOfMonth.plusDays(appProperties.business().bufferDays())
        );

        // Aggregate blocks by date
        Map<LocalDate, Integer> blockedByDate = aggregateBlocksByDate(blocks);

        // Build day-by-day status
        List<DayAvailability> days = new ArrayList<>();
        LocalDate cursor = startOfMonth;
        while (!cursor.isAfter(endOfMonth)) {
            days.add(buildDayAvailability(cursor, item, blockedByDate, today, minAdvanceDays));
            cursor = cursor.plusDays(1);
        }

        return MonthlyAvailability.builder()
                .year(year)
                .month(month)
                .days(days)
                .build();
    }

    // ============================================================
    //  ADMIN — Full calendar events across all items
    // ============================================================

    /**
     * Returns FullCalendar-compatible events for the admin calendar.
     * Shows all confirmed bookings as colored blocks.
     */
    public AdminCalendarResponse getAdminCalendarEvents(LocalDate startDate, LocalDate endDate) {
        List<AvailabilityBlock> blocks = blockRepository.findAllBlocksInRange(startDate, endDate);
        List<LocalDate> fullyBooked = blockRepository.findFullyBookedDates(startDate, endDate);

        // Group blocks by booking to create calendar events
        Map<UUID, List<AvailabilityBlock>> byBooking = blocks.stream()
                .filter(b -> b.getBooking() != null)
                .collect(Collectors.groupingBy(b -> b.getBooking().getId()));

        List<CalendarEvent> events = byBooking.values().stream()
                .map(this::bookingBlocksToCalendarEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return AdminCalendarResponse.builder()
                .events(events)
                .fullyBookedDates(fullyBooked.stream()
                        .map(LocalDate::toString)
                        .collect(Collectors.toList()))
                .totalBookingsInRange(byBooking.size())
                .build();
    }

    // ============================================================
    //  WRITE — Create / release availability blocks
    // ============================================================

    /**
     * Creates availability blocks for all items in a confirmed booking.
     * Includes buffer days before and after the event date.
     * Called when a booking transitions to CONFIRMED (deposit paid).
     */
    @Transactional
    @CacheEvict(value = {"item-calendar", "availability"}, allEntries = true)
    public void createBlocksForBooking(Booking booking) {
        int bufferDays = appProperties.business().bufferDays();
        LocalDate eventDate = booking.getEventDate();

        // Date range to block: bufferDay before → event day → bufferDay after
        LocalDate blockStart = eventDate.minusDays(bufferDays);
        LocalDate blockEnd = eventDate.plusDays(bufferDays);

        for (var lineItem : booking.getBookingItems()) {
            if (lineItem.getItem() == null) continue;

            LocalDate cursor = blockStart;
            while (!cursor.isAfter(blockEnd)) {
                AvailabilityBlock block = AvailabilityBlock.builder()
                        .item(lineItem.getItem())
                        .booking(booking)
                        .blockDate(cursor)
                        .quantity(lineItem.getQuantity())
                        .reason("BOOKING")
                        .build();
                blockRepository.save(block);
                cursor = cursor.plusDays(1);
            }

            log.info("Created availability blocks for item {} in booking {} ({} to {})",
                    lineItem.getItem().getId(), booking.getBookingNumber(), blockStart, blockEnd);
        }
    }

    /**
     * Releases all availability blocks for a booking.
     * Called when a booking is CANCELLED.
     */
    @Transactional
    @CacheEvict(value = {"item-calendar", "availability"}, allEntries = true)
    public void releaseBlocksForBooking(UUID bookingId) {
        int count = blockRepository.findByBookingId(bookingId).size();
        blockRepository.deleteByBookingId(bookingId);
        log.info("Released {} availability blocks for booking {}", count, bookingId);
    }

    /**
     * Creates a manual maintenance/hold block for an item on specific dates.
     * Used by admins to block dates for maintenance, personal holds, etc.
     */
    @Transactional
    @CacheEvict(value = {"item-calendar", "availability"}, allEntries = true)
    public void createManualBlock(UUID itemId, LocalDate startDate, LocalDate endDate, String reason) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            AvailabilityBlock block = AvailabilityBlock.builder()
                    .item(item)
                    .blockDate(cursor)
                    .quantity(item.getQuantityInStock())  // Block entire stock for manual holds
                    .reason(reason)
                    .build();
            blockRepository.save(block);
            cursor = cursor.plusDays(1);
        }

        log.info("Created manual {} block for item {} from {} to {}", reason, itemId, startDate, endDate);
    }

    // ============================================================
    //  PRIVATE HELPERS
    // ============================================================

    private AvailabilityCheckResponse checkItemAvailability(
            Item item, LocalDate startDate, LocalDate endDate,
            int requestedQty, UUID excludeBookingId) {

        LocalDate today = LocalDate.now();
        int minAdvanceDays = appProperties.business().minBookingDaysAhead();

        // Validation: past dates
        if (startDate.isBefore(today)) {
            return unavailableResponse(item, startDate, endDate, requestedQty, 0,
                    "Cannot book dates in the past.");
        }

        // Validation: minimum advance booking
        if (startDate.isBefore(today.plusDays(minAdvanceDays))) {
            return unavailableResponse(item, startDate, endDate, requestedQty, 0,
                    "Bookings must be made at least " + minAdvanceDays + " days in advance.");
        }

        // Validation: requested qty vs stock
        if (requestedQty > item.getQuantityInStock()) {
            return unavailableResponse(item, startDate, endDate, requestedQty,
                    item.getQuantityInStock(),
                    "Only " + item.getQuantityInStock() + " unit(s) available in stock.");
        }

        // Find all blocks in the date range
        List<AvailabilityBlock> blocks = blockRepository.findBlocksForItem(
                item.getId(), startDate, endDate);

        Map<LocalDate, Integer> blockedByDate = aggregateBlocksByDate(blocks);

        // Find unavailable dates
        List<LocalDate> unavailableDates = new ArrayList<>();
        int minAvailableQty = item.getQuantityInStock();

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            int blocked = blockedByDate.getOrDefault(cursor, 0);
            int available = item.getQuantityInStock() - blocked;
            minAvailableQty = Math.min(minAvailableQty, available);

            if (available < requestedQty) {
                unavailableDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        boolean available = unavailableDates.isEmpty();

        return AvailabilityCheckResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .startDate(startDate)
                .endDate(endDate)
                .available(available)
                .requestedQuantity(requestedQty)
                .availableQuantity(Math.max(0, minAvailableQty))
                .stockQuantity(item.getQuantityInStock())
                .unavailableDates(unavailableDates)
                .message(available
                        ? "Available for your selected dates!"
                        : "Not available on " + unavailableDates.size() + " date(s) in your range.")
                .build();
    }

    private DayAvailability buildDayAvailability(
            LocalDate date, Item item,
            Map<LocalDate, Integer> blockedByDate,
            LocalDate today, int minAdvanceDays) {

        // Past dates
        if (date.isBefore(today)) {
            return DayAvailability.builder()
                    .date(date)
                    .status(DayAvailability.DayStatus.PAST)
                    .availableQuantity(0)
                    .totalQuantity(item.getQuantityInStock())
                    .build();
        }

        // Min advance days
        if (date.isBefore(today.plusDays(minAdvanceDays))) {
            return DayAvailability.builder()
                    .date(date)
                    .status(DayAvailability.DayStatus.MIN_ADVANCE)
                    .availableQuantity(0)
                    .totalQuantity(item.getQuantityInStock())
                    .build();
        }

        int blocked = blockedByDate.getOrDefault(date, 0);
        int available = Math.max(0, item.getQuantityInStock() - blocked);

        DayAvailability.DayStatus status;
        if (available == 0) {
            status = DayAvailability.DayStatus.FULLY_BOOKED;
        } else if (blocked > 0) {
            status = DayAvailability.DayStatus.PARTIAL;
        } else {
            status = DayAvailability.DayStatus.AVAILABLE;
        }

        return DayAvailability.builder()
                .date(date)
                .status(status)
                .availableQuantity(available)
                .totalQuantity(item.getQuantityInStock())
                .build();
    }

    private Map<LocalDate, Integer> aggregateBlocksByDate(List<AvailabilityBlock> blocks) {
        return blocks.stream()
                .collect(Collectors.groupingBy(
                        AvailabilityBlock::getBlockDate,
                        Collectors.summingInt(AvailabilityBlock::getQuantity)
                ));
    }

    private CalendarEvent bookingBlocksToCalendarEvent(List<AvailabilityBlock> blocks) {
        if (blocks.isEmpty()) return null;

        AvailabilityBlock first = blocks.get(0);
        Booking booking = first.getBooking();
        if (booking == null) return null;

        LocalDate minDate = blocks.stream().map(AvailabilityBlock::getBlockDate).min(Comparator.naturalOrder()).orElse(first.getBlockDate());
        LocalDate maxDate = blocks.stream().map(AvailabilityBlock::getBlockDate).max(Comparator.naturalOrder()).orElse(first.getBlockDate());

        // Color coding by booking status
        String color = switch (booking.getStatus()) {
            case CONFIRMED   -> "#C9A84C";   // Gold
            case PENDING     -> "#6b7280";   // Gray
            case QUOTE_SENT  -> "#3b82f6";   // Blue
            case IN_PROGRESS -> "#10b981";   // Green
            case COMPLETED   -> "#8b5cf6";   // Purple
            case CANCELLED   -> "#ef4444";   // Red
            default          -> "#9ca3af";
        };

        String customerName = booking.getCustomer() != null
                ? booking.getCustomer().getFullName() : "Unknown";

        return CalendarEvent.builder()
                .id(booking.getId().toString())
                .title(booking.getBookingNumber() + " · " + customerName)
                .start(minDate.toString())
                .end(maxDate.plusDays(1).toString())   // FullCalendar end is exclusive
                .color(color)
                .textColor("#0B1628")
                .allDay(true)
                .extendedProps(CalendarEventExtended.builder()
                        .bookingNumber(booking.getBookingNumber())
                        .customerName(customerName)
                        .status(booking.getStatus().name())
                        .itemName(first.getItem() != null ? first.getItem().getName() : "")
                        .itemId(first.getItem() != null ? first.getItem().getId() : null)
                        .bookingId(booking.getId())
                        .build())
                .build();
    }

    private AvailabilityCheckResponse unavailableResponse(
            Item item, LocalDate start, LocalDate end,
            int requested, int available, String message) {
        return AvailabilityCheckResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .startDate(start)
                .endDate(end)
                .available(false)
                .requestedQuantity(requested)
                .availableQuantity(available)
                .stockQuantity(item.getQuantityInStock())
                .unavailableDates(List.of())
                .message(message)
                .build();
    }
}
