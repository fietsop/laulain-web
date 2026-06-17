package com.laulain.rentals.dto;

import com.laulain.rentals.model.Payment;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class PaymentDto {

    /** Full payment record — used in admin and portal views */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentResponse {
        private UUID id;
        private UUID bookingId;
        private String bookingNumber;
        private String customerName;
        private Payment.Type type;
        private String typeLabel;
        private Payment.Status status;
        private String statusLabel;
        private String statusColor;
        private BigDecimal amount;
        private String stripePaymentIntentId;
        private String stripeChargeId;
        private String description;
        private String failureReason;
        private Instant createdAt;
    }

    /** Compact summary for booking detail and customer portal */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PaymentSummary {
        private UUID id;
        private Payment.Type type;
        private String typeLabel;
        private Payment.Status status;
        private String statusLabel;
        private String statusColor;
        private BigDecimal amount;
        private Instant createdAt;
    }

    /** Stripe Checkout session response — returned to the frontend to redirect */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CheckoutSessionResponse {
        private String sessionId;
        private String checkoutUrl;    // Redirect customer here
        private UUID paymentId;        // Our internal Payment record ID
    }

    /** Refund request — admin only */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RefundRequest {
        private UUID paymentId;
        private BigDecimal amount;     // null = full refund
        private String reason;
    }

    /** Admin dashboard revenue summary */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueSummary {
        private BigDecimal totalThisMonth;
        private BigDecimal totalThisYear;
        private BigDecimal totalAllTime;
        private long pendingPaymentsCount;
        private long succeededPaymentsCount;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }
}
