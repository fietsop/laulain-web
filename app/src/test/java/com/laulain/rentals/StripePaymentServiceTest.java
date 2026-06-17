package com.laulain.rentals;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.PaymentDto.*;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.BookingRepository;
import com.laulain.rentals.repository.PaymentRepository;
import com.laulain.rentals.service.BookingService;
import com.laulain.rentals.service.NotificationService;
import com.laulain.rentals.service.StripePaymentService;
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
@DisplayName("StripePaymentService Tests")
class StripePaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock BookingRepository bookingRepository;
    @Mock BookingService bookingService;
    @Mock NotificationService notificationService;
    @Mock AppProperties appProperties;

    @InjectMocks StripePaymentService paymentService;

    private Booking booking;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .firstName("Alain").lastName("Foryim")
                .email("alain@example.com").phone("2145550100")
                .build();

        booking = Booking.builder()
                .id(bookingId)
                .bookingNumber("LLR-2024-0001")
                .customer(customer)
                .status(Booking.Status.PENDING)
                .eventDate(LocalDate.now().plusDays(30))
                .eventType("Wedding")
                .subtotal(new BigDecimal("200.00"))
                .taxAmount(new BigDecimal("16.50"))
                .totalAmount(new BigDecimal("216.50"))
                .depositAmount(new BigDecimal("64.95"))
                .balanceDue(new BigDecimal("151.55"))
                .deliveryFee(BigDecimal.ZERO)
                .setupFee(BigDecimal.ZERO)
                .bookingItems(new ArrayList<>())
                .build();

        when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(Collections.emptyList());

        List<PaymentSummary> result = paymentService.getPaymentsForBooking(bookingId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPaymentsForBooking — maps payment fields correctly")
    void getPaymentsForBooking_mapFields() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .type(Payment.Type.DEPOSIT)
                .status(Payment.Status.SUCCEEDED)
                .amount(new BigDecimal("64.95"))
                .build();

        when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenReturn(List.of(payment));

        List<PaymentSummary> result = paymentService.getPaymentsForBooking(bookingId);

        assertThat(result).hasSize(1);
        PaymentSummary summary = result.get(0);
        assertThat(summary.getType()).isEqualTo(Payment.Type.DEPOSIT);
        assertThat(summary.getStatus()).isEqualTo(Payment.Status.SUCCEEDED);
        assertThat(summary.getAmount()).isEqualByComparingTo("64.95");
        assertThat(summary.getTypeLabel()).isNull();
        assertThat(summary.getStatusLabel()).isNull();
        assertThat(summary.getStatusColor()).isNull();
    }

    @Test
    @DisplayName("createDepositCheckout — throws when booking already confirmed")
    void createDepositCheckout_alreadyConfirmed() {
        booking.setStatus(Booking.Status.CONFIRMED);
        when(bookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.createDepositCheckout(bookingId))
                .isInstanceOf(UnsupportedOperationException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDepositCheckout — throws when booking cancelled")
    void createDepositCheckout_cancelled() {
        booking.setStatus(Booking.Status.CANCELLED);
        when(bookingRepository.findByIdWithDetails(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.createDepositCheckout(bookingId))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("issueRefund — throws when payment not succeeded")
    void issueRefund_notSucceeded() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = Payment.builder()
                .id(paymentId)
                .booking(booking)
                .type(Payment.Type.DEPOSIT)
                .status(Payment.Status.PENDING)
                .amount(new BigDecimal("64.95"))
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pendingPayment));

        RefundRequest req = new RefundRequest(paymentId, null, "Test refund");
        assertThatThrownBy(() -> paymentService.issueRefund(req))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getRevenueSummary — returns zero summary")
    void getRevenueSummary_structure() {
        RevenueSummary summary = paymentService.getRevenueSummary();

        assertThat(summary.getTotalThisMonth()).isEqualByComparingTo("0.00");
        assertThat(summary.getPendingPaymentsCount()).isEqualTo(0L);
        assertThat(summary.getSucceededPaymentsCount()).isEqualTo(0L);
        assertThat(summary.getPeriodStart()).isNotNull();
        assertThat(summary.getPeriodEnd()).isNotNull();
    }
}
