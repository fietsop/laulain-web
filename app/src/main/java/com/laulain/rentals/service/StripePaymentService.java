package com.laulain.rentals.service;

import com.laulain.rentals.dto.PaymentDto.*;
import com.laulain.rentals.model.Payment;
import com.laulain.rentals.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * StripePaymentService stub — Stripe payments disabled for initial deployment.
 * Re-enable full implementation when Stripe is configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentService {

    private final PaymentRepository paymentRepository;

    public CheckoutSessionResponse createDepositCheckout(UUID bookingId) {
        log.info("[STRIPE STUB] createDepositCheckout called for booking {} — Stripe disabled", bookingId);
        throw new UnsupportedOperationException("Payments not enabled in this deployment.");
    }

    public CheckoutSessionResponse createBalanceCheckout(UUID bookingId) {
        log.info("[STRIPE STUB] createBalanceCheckout called for booking {} — Stripe disabled", bookingId);
        throw new UnsupportedOperationException("Payments not enabled in this deployment.");
    }

    public void handleWebhook(String payload, String sigHeader) {
        log.info("[STRIPE STUB] handleWebhook called — Stripe disabled");
        throw new UnsupportedOperationException("Payments not enabled in this deployment.");
    }

    public PaymentResponse issueRefund(RefundRequest request) {
        log.info("[STRIPE STUB] issueRefund called — Stripe disabled");
        throw new UnsupportedOperationException("Payments not enabled in this deployment.");
    }

    public List<PaymentSummary> getPaymentsForBooking(UUID bookingId) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)
                .stream().map(this::toPaymentSummary).collect(Collectors.toList());
    }

    public PaymentResponse getPaymentById(UUID id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new com.laulain.rentals.exception.ResourceNotFoundException("Payment not found: " + id));
        return toPaymentResponse(p);
    }

    public RevenueSummary getRevenueSummary() {
        return RevenueSummary.builder()
                .totalThisMonth(BigDecimal.ZERO)
                .totalThisYear(BigDecimal.ZERO)
                .totalAllTime(BigDecimal.ZERO)
                .pendingPaymentsCount(0)
                .succeededPaymentsCount(0)
                .periodStart(LocalDate.now().withDayOfMonth(1))
                .periodEnd(LocalDate.now())
                .build();
    }

    public PaymentResponse toPaymentResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .bookingId(p.getBooking().getId())
                .bookingNumber(p.getBooking().getBookingNumber())
                .type(p.getType())
                .status(p.getStatus())
                .amount(p.getAmount())
                .description(p.getDescription())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PaymentSummary toPaymentSummary(Payment p) {
        return PaymentSummary.builder()
                .id(p.getId())
                .type(p.getType())
                .status(p.getStatus())
                .amount(p.getAmount())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
