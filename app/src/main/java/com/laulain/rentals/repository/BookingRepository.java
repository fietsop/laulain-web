package com.laulain.rentals.repository;

import com.laulain.rentals.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    // ---- Customer portal ----
    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    List<Booking> findByCustomerIdAndStatusInOrderByEventDateAsc(
            UUID customerId, List<Booking.Status> statuses);

    // ---- Admin list with filters ----
    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.customer c
        WHERE (:status IS NULL OR b.status = :status)
          AND (:eventDate IS NULL OR b.eventDate = :eventDate)
          AND (:search IS NULL
               OR LOWER(b.bookingNumber) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(c.firstName) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(c.lastName) LIKE LOWER(CONCAT('%',:search,'%')))
        ORDER BY b.createdAt DESC
        """)
    Page<Booking> findForAdmin(
            @Param("status") Booking.Status status,
            @Param("eventDate") LocalDate eventDate,
            @Param("search") String search,
            Pageable pageable
    );

    // ---- Full detail fetch (avoids N+1) ----
    @Query("""
        SELECT DISTINCT b FROM Booking b
        JOIN FETCH b.customer
        LEFT JOIN FETCH b.bookingItems bi
        LEFT JOIN FETCH bi.item
        WHERE b.id = :id
        """)
    Optional<Booking> findByIdWithDetails(@Param("id") UUID id);

    // ---- Upcoming bookings for reminders ----
    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = 'CONFIRMED'
          AND b.eventDate = :targetDate
        """)
    List<Booking> findConfirmedBookingsOnDate(@Param("targetDate") LocalDate targetDate);

    // ---- Revenue stats ----
    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0)
        FROM Booking b
        WHERE b.status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED')
          AND b.eventDate BETWEEN :startDate AND :endDate
        """)
    BigDecimal sumRevenueInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    long countByStatus(Booking.Status status);

    // ---- Next sequence number ----
    @Query(value = "SELECT nextval('booking_number_seq')", nativeQuery = true)
    long nextBookingSequence();
}
