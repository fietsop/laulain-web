package com.laulain.rentals.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quote {

    public enum Status {
        DRAFT, SENT, VIEWED, ACCEPTED, DECLINED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "quote_number", nullable = false, unique = true, length = 20)
    private String quoteNumber;   // LLR-Q-2024-0001

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "setup_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal setupFee = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "pdf_s3_key", length = 500)
    private String pdfS3Key;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ---- Convenience ----
    public boolean isExpired() {
        return validUntil != null && LocalDate.now().isAfter(validUntil);
    }

    public boolean isActionable() {
        return status == Status.SENT || status == Status.VIEWED;
    }

    public BigDecimal getBalanceDue() {
        if (totalAmount == null || depositAmount == null) return BigDecimal.ZERO;
        return totalAmount.subtract(depositAmount);
    }
}
