package com.laulain.rentals.repository;

import com.laulain.rentals.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

    Optional<Payment> findByStripeSessionId(String sessionId);

    // All payments for admin table
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.booking b
        JOIN FETCH b.customer c
        WHERE (:status IS NULL OR p.status = :status)
          AND (:type IS NULL OR p.type = :type)
        ORDER BY p.createdAt DESC
        """)
    Page<Payment> findAllForAdmin(
            @Param("status") Payment.Status status,
            @Param("type") Payment.Type type,
            Pageable pageable
    );

    // Total collected for a booking
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.booking.id = :bookingId
          AND p.status = 'SUCCEEDED'
          AND p.type IN ('DEPOSIT', 'BALANCE')
        """)
    BigDecimal sumSucceededForBooking(@Param("bookingId") UUID bookingId);

    // Revenue stats
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.status = 'SUCCEEDED'
          AND p.type IN ('DEPOSIT', 'BALANCE')
          AND p.createdAt BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumRevenueInPeriod(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    long countByStatus(Payment.Status status);
}
