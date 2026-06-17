package com.laulain.rentals.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    // ---- Status enum ----
    public enum Status {
        PENDING,        // Submitted, awaiting admin review
        QUOTE_SENT,     // Quote generated and emailed
        CONFIRMED,      // Deposit paid — availability blocks created
        IN_PROGRESS,    // Event day — items out
        COMPLETED,      // Items returned
        CANCELLED,      // Booking cancelled
        NO_SHOW         // Customer no-show
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_number", nullable = false, unique = true, length = 20)
    private String bookingNumber;   // LLR-2024-0001

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    // ---- Event details ----
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_start_time")
    private LocalTime eventStartTime;

    @Column(name = "event_end_time")
    private LocalTime eventEndTime;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "event_venue", length = 255)
    private String eventVenue;

    @Column(name = "guest_count")
    private Integer guestCount;

    // ---- Delivery ----
    @Column(name = "delivery_address_line1", length = 255)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2", length = 100)
    private String deliveryAddressLine2;

    @Column(name = "delivery_city", length = 100)
    private String deliveryCity;

    @Column(name = "delivery_state", length = 50)
    private String deliveryState;

    @Column(name = "delivery_zip", length = 10)
    private String deliveryZip;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "requires_setup")
    @Builder.Default
    private Boolean requiresSetup = false;

    @Column(name = "setup_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal setupFee = BigDecimal.ZERO;

    // ---- Pricing ----
    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "balance_due", precision = 10, scale = 2)
    private BigDecimal balanceDue;

    // ---- Notes ----
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // ---- Line items ----
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingItem> bookingItems = new ArrayList<>();

    // ---- Timestamps ----
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // ---- Convenience ----
    public boolean isConfirmed() { return status == Status.CONFIRMED; }
    public boolean isCancellable() {
        return status == Status.PENDING || status == Status.QUOTE_SENT || status == Status.CONFIRMED;
    }
}
