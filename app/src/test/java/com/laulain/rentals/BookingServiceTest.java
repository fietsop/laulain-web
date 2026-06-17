package com.laulain.rentals;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.BookingDto.*;
import com.laulain.rentals.exception.BookingConflictException;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import com.laulain.rentals.service.AvailabilityService;
import com.laulain.rentals.service.BookingService;
import com.laulain.rentals.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Tests")
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock CustomerRepository customerRepository;
    @Mock ItemRepository itemRepository;
    @Mock AvailabilityService availabilityService;
    @Mock NotificationService notificationService;
    @Mock AppProperties appProperties;
    @Mock AppProperties.BusinessProperties businessProperties;

    @InjectMocks BookingService bookingService;

    private Item item;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        item = Item.builder()
                .id(itemId)
                .name("Gold Chafing Dish")
                .slug("gold-chafing-dish")
                .pricePerDay(new BigDecimal("35.00"))
                .quantityInStock(3)
                .active(true)
                .images(List.of())
                .category(ItemCategory.builder().name("Chafing Dishes").build())
                .build();

        when(appProperties.business()).thenReturn(businessProperties);
        when(businessProperties.taxRate()).thenReturn(0.0825);
    }

    @Test
    @DisplayName("calculatePricingEstimate — correct tax, deposit, balance")
    void calculatePricing_correct() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        BookingItemRequest req = new BookingItemRequest(itemId, 2);
        PricingEstimate est = bookingService.calculatePricingEstimate(List.of(req), 1, false);

        // 2 × $35 = $70 subtotal
        assertThat(est.getSubtotal()).isEqualByComparingTo("70.00");
        // Tax: $70 × 8.25% = $5.78 (rounded)
        assertThat(est.getTaxAmount()).isEqualByComparingTo("5.78");
        // Total: $75.78
        assertThat(est.getTotalAmount()).isEqualByComparingTo("75.78");
        // Deposit: 30% of $75.78 = $22.73
        assertThat(est.getDepositAmount()).isEqualByComparingTo("22.73");
        // Balance: $75.78 - $22.73 = $53.05
        assertThat(est.getBalanceDue()).isEqualByComparingTo("53.05");
    }

    @Test
    @DisplayName("createBooking — throws BookingConflictException when item unavailable")
    void createBooking_conflict() {
        LocalDate futureDate = LocalDate.now().plusDays(10);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(availabilityService.checkAvailability(any()))
                .thenReturn(com.laulain.rentals.dto.AvailabilityDto.AvailabilityCheckResponse.builder()
                        .available(false)
                        .message("Fully booked on this date.")
                        .unavailableDates(List.of(futureDate))
                        .build());

        BookingRequest request = buildRequest(futureDate);
        assertThatThrownBy(() -> bookingService.createBooking(request, null))
                .isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("cancelBooking — releases availability blocks for confirmed booking")
    void cancelBooking_releasesBlocks() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = buildConfirmedBooking(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);
        doNothing().when(availabilityService).releaseBlocksForBooking(any());
        doNothing().when(notificationService).sendCancellationNotification(any());

        bookingService.cancelBooking(bookingId, "Customer requested cancellation");

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
        verify(availabilityService).releaseBlocksForBooking(bookingId);
        verify(notificationService).sendCancellationNotification(booking);
    }

    @Test
    @DisplayName("cancelBooking — does not release blocks for PENDING booking")
    void cancelBooking_pending_noBlockRelease() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = buildPendingBooking(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);
        doNothing().when(notificationService).sendCancellationNotification(any());

        bookingService.cancelBooking(bookingId, "Changed mind");

        verify(availabilityService, never()).releaseBlocksForBooking(any());
    }

    // ---- Helpers ----

    private BookingRequest buildRequest(LocalDate eventDate) {
        BookingRequest r = new BookingRequest();
        r.setFirstName("Test"); r.setLastName("User");
        r.setEmail("test@test.com"); r.setPhone("2145550100");
        r.setEventDate(eventDate); r.setEventType("Wedding");
        r.setDeliveryAddressLine1("123 Main St"); r.setDeliveryCity("Fate");
        r.setDeliveryState("TX"); r.setDeliveryZip("75087");
        r.setItems(List.of(new BookingItemRequest(itemId, 1)));
        return r;
    }

    private Booking buildConfirmedBooking(UUID id) {
        return Booking.builder()
                .id(id).bookingNumber("LLR-2024-0001")
                .status(Booking.Status.CONFIRMED)
                .customer(Customer.builder().email("c@c.com").firstName("Test").lastName("User").build())
                .eventDate(LocalDate.now().plusDays(30))
                .bookingItems(new ArrayList<>())
                .deliveryFee(BigDecimal.ZERO).setupFee(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO).taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO).depositAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO).build();
    }

    private Booking buildPendingBooking(UUID id) {
        Booking b = buildConfirmedBooking(id);
        b.setStatus(Booking.Status.PENDING);
        return b;
    }
}
