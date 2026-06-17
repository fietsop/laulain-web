package com.laulain.rentals.repository;

import com.laulain.rentals.model.AvailabilityBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    /**
     * Returns the total quantity blocked for a specific item on a given date.
     * Used to determine remaining availability.
     */
    @Query("""
        SELECT COALESCE(SUM(ab.quantity), 0)
        FROM AvailabilityBlock ab
        WHERE ab.item.id = :itemId
          AND ab.blockDate = :date
        """)
    int sumBlockedQuantity(@Param("itemId") UUID itemId,
                           @Param("date") LocalDate date);

    /**
     * Returns all blocked dates for a specific item within a date range.
     * Used to build the calendar view.
     */
    @Query("""
        SELECT ab FROM AvailabilityBlock ab
        WHERE ab.item.id = :itemId
          AND ab.blockDate BETWEEN :startDate AND :endDate
        ORDER BY ab.blockDate ASC
        """)
    List<AvailabilityBlock> findBlocksForItem(
            @Param("itemId") UUID itemId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Returns all blocked dates across ALL items for a date range.
     * Used to build the admin-wide availability calendar.
     */
    @Query("""
        SELECT ab FROM AvailabilityBlock ab
        JOIN FETCH ab.item i
        LEFT JOIN FETCH ab.booking b
        WHERE ab.blockDate BETWEEN :startDate AND :endDate
        ORDER BY ab.blockDate ASC, i.name ASC
        """)
    List<AvailabilityBlock> findAllBlocksInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Returns blocked dates for a specific booking.
     * Used when cancelling a booking to release its blocks.
     */
    List<AvailabilityBlock> findByBookingId(UUID bookingId);

    /**
     * Checks if an item has enough quantity available across an entire date range.
     * Returns the count of dates in the range where available qty < requested qty.
     * A result of 0 means the item is fully available for the entire range.
     */
    @Query("""
        SELECT COUNT(DISTINCT blocked.blockDate)
        FROM (
            SELECT ab.blockDate AS blockDate, SUM(ab.quantity) AS blockedQty
            FROM AvailabilityBlock ab
            WHERE ab.item.id = :itemId
              AND ab.blockDate BETWEEN :startDate AND :endDate
              AND (:excludeBookingId IS NULL OR ab.booking.id != :excludeBookingId)
            GROUP BY ab.blockDate
            HAVING SUM(ab.quantity) >= :stockQty
        ) blocked
        """)
    long countUnavailableDates(
            @Param("itemId") UUID itemId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("stockQty") int stockQty,
            @Param("excludeBookingId") UUID excludeBookingId
    );

    /**
     * Delete all blocks for a booking — used when booking is cancelled.
     */
    @Modifying
    @Query("DELETE FROM AvailabilityBlock ab WHERE ab.booking.id = :bookingId")
    void deleteByBookingId(@Param("bookingId") UUID bookingId);

    /**
     * Returns all dates where any item is at full capacity — for the admin calendar heatmap.
     */
    @Query("""
        SELECT ab.blockDate
        FROM AvailabilityBlock ab
        JOIN ab.item i
        WHERE ab.blockDate BETWEEN :startDate AND :endDate
        GROUP BY ab.blockDate, i.id, i.quantityInStock
        HAVING SUM(ab.quantity) >= i.quantityInStock
        ORDER BY ab.blockDate
        """)
    List<LocalDate> findFullyBookedDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
