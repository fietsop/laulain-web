package com.laulain.rentals.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks which items are blocked on which dates.
 *
 * A block is created for each date in a booking's range (including buffer days)
 * when a booking transitions to CONFIRMED status.
 *
 * Multiple blocks per date are allowed up to the item's quantityInStock.
 * Example: 3 chafing dishes in stock → up to 3 blocks per date before it's fully booked.
 */
@Entity
@Table(name = "availability_blocks",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_availability_item_booking_date",
           columnNames = {"item_id", "booking_id", "block_date"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(length = 50)
    @Builder.Default
    private String reason = "BOOKING";   // BOOKING | MAINTENANCE | HOLD

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
