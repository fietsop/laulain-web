package com.laulain.rentals.integration;

import com.laulain.rentals.dto.BookingDto.*;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import com.laulain.rentals.service.AvailabilityService;
import com.laulain.rentals.service.BookingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Booking Lifecycle Integration Tests")
class BookingLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired BookingService bookingService;
    @Autowired AvailabilityService availabilityService;
    @Autowired BookingRepository bookingRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired ItemCategoryRepository categoryRepository;
    @Autowired AvailabilityBlockRepository blockRepository;

    private Item item;
    private static final LocalDate EVENT_DATE = LocalDate.now().plusDays(45);

    @BeforeEach
    @Transactional
    void setUp() {
        ItemCategory cat = categoryRepository.save(ItemCategory.builder()
                .name("Booking Test Cat " + UUID.randomUUID().toString().substring(0, 6))
                .active(true).sortOrder(99).build());

        item = itemRepository.save(Item.builder()
                .name("Booking Test Item " + UUID.randomUUID().toString().substring(0, 6))
                .slug("booking-test-" + UUID.randomUUID().toString().substring(0, 8))
                .category(cat)
                .pricePerDay(new BigDecimal("50.00"))
                .quantityInStock(2)
                .active(true).featured(false).images(new ArrayList<>())
                .build());
    }

    @Test
    @DisplayName("Full booking flow: create → confirm → availability blocked → cancel → released")
    @Transactional
    void fullBookingLifecycle() {
        // 1. Create booking
        BookingResponse booking = bookingService.createBooking(buildRequest(EVENT_DATE), null);

        assertThat(booking.getBookingNumber()).startsWith("LLR-");
        assertThat(booking.getStatus()).isEqualTo(Booking.Status.PENDING);
        assertThat(booking.getTotalAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(booking.getDepositAmount())
                .isEqualByComparingTo(booking.getTotalAmount()
                        .multiply(new BigDecimal("0.30")).setScale(2, java.math.RoundingMode.HALF_UP));

        UUID bookingId = booking.getId();

        // 2. Verify no availability blocks yet (booking still PENDING)
        assertThat(blockRepository.findByBookingId(bookingId)).isEmpty();

        // 3. Confirm booking → blocks should be created
        bookingService.confirmBooking(bookingId);
        BookingResponse confirmed = bookingService.getBookingById(bookingId);
        assertThat(confirmed.getStatus()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();

        // 4. Verify availability blocks created (with buffer days: -1, 0, +1 = 3 dates)
        var blocks = blockRepository.findByBookingId(bookingId);
        assertThat(blocks).hasSize(3);
        assertThat(blocks).extracting(b -> b.getBlockDate())
                .contains(EVENT_DATE.minusDays(1), EVENT_DATE, EVENT_DATE.plusDays(1));

        // 5. Verify the item is now unavailable on EVENT_DATE
        var checkReq = new com.laulain.rentals.dto.AvailabilityDto.AvailabilityCheckRequest(
                item.getId(), EVENT_DATE, EVENT_DATE, 2);
        var checkResult = availabilityService.checkAvailability(checkReq);
        assertThat(checkResult.isAvailable()).isFalse();

        // 6. Cancel booking → blocks released
        bookingService.cancelBooking(bookingId, "Integration test cancellation");
        BookingResponse cancelled = bookingService.getBookingById(bookingId);
        assertThat(cancelled.getStatus()).isEqualTo(Booking.Status.CANCELLED);

        // 7. Verify blocks were released
        assertThat(blockRepository.findByBookingId(bookingId)).isEmpty();

        // 8. Item should be available again
        var recheckResult = availabilityService.checkAvailability(checkReq);
        assertThat(recheckResult.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Cannot double-book when all units are taken")
    @Transactional
    void cannotDoubleBook() {
        // First booking: uses both available units
        BookingResponse first = bookingService.createBooking(buildRequest(EVENT_DATE, 2), null);
        bookingService.confirmBooking(first.getId()); // Creates blocks for all 2 units

        // Second booking: tries to book 1 unit on the same date
        BookingRequest second = buildRequest(EVENT_DATE, 1);
        assertThatThrownBy(() -> bookingService.createBooking(second, null))
                .isInstanceOf(com.laulain.rentals.exception.BookingConflictException.class);
    }

    @Test
    @DisplayName("Pricing calculation: tax + deposit + balance")
    @Transactional
    void pricingCalculation() {
        BookingResponse booking = bookingService.createBooking(buildRequest(EVENT_DATE), null);

        // $50 × 1 qty = $50 subtotal
        assertThat(booking.getSubtotal()).isEqualByComparingTo("50.00");

        // Tax: $50 × 8.25% = $4.13
        assertThat(booking.getTaxAmount()).isEqualByComparingTo("4.13");

        // Total: $54.13
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("54.13");

        // Deposit: 30% of $54.13 = $16.24
        assertThat(booking.getDepositAmount()).isEqualByComparingTo("16.24");

        // Balance: $54.13 - $16.24 = $37.89
        assertThat(booking.getBalanceDue()).isEqualByComparingTo("37.89");
    }

    @Test
    @DisplayName("Admin can update notes without changing booking status")
    @Transactional
    void adminNotesUpdate() {
        BookingResponse booking = bookingService.createBooking(buildRequest(EVENT_DATE), null);

        AdminBookingUpdate update = new AdminBookingUpdate();
        update.setAdminNotes("Customer prefers afternoon delivery. Gold linens requested.");

        BookingResponse updated = bookingService.updateAdminNotes(booking.getId(), update);
        assertThat(updated.getAdminNotes())
                .isEqualTo("Customer prefers afternoon delivery. Gold linens requested.");
        assertThat(updated.getStatus()).isEqualTo(Booking.Status.PENDING);
    }

    // ---- Helpers ----

    private BookingRequest buildRequest(LocalDate eventDate) {
        return buildRequest(eventDate, 1);
    }

    private BookingRequest buildRequest(LocalDate eventDate, int qty) {
        BookingRequest r = new BookingRequest();
        r.setFirstName("Integration"); r.setLastName("Test");
        r.setEmail("integration-" + UUID.randomUUID() + "@test.com");
        r.setPhone("2145550199");
        r.setEventDate(eventDate);
        r.setEventType("Corporate Event");
        r.setDeliveryAddressLine1("123 Test Lane");
        r.setDeliveryCity("Fate");
        r.setDeliveryState("TX");
        r.setDeliveryZip("75087");
        r.setRequiresSetup(false);
        r.setItems(List.of(new BookingItemRequest(item.getId(), qty)));
        return r;
    }
}
