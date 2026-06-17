package com.laulain.rentals.repository;

import com.laulain.rentals.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    Optional<Quote> findByQuoteNumber(String quoteNumber);

    List<Quote> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<Quote> findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(
            UUID bookingId, List<Quote.Status> statuses);

    // Quotes expiring soon — for admin dashboard warning
    @Query("""
        SELECT q FROM Quote q
        WHERE q.status IN ('SENT', 'VIEWED')
          AND q.validUntil BETWEEN :today AND :warningDate
        ORDER BY q.validUntil ASC
        """)
    List<Quote> findQuotesExpiringSoon(
            @Param("today") LocalDate today,
            @Param("warningDate") LocalDate warningDate
    );

    // Mark expired quotes — run by scheduler nightly
    @Query("""
        SELECT q FROM Quote q
        WHERE q.status IN ('SENT', 'VIEWED')
          AND q.validUntil < :today
        """)
    List<Quote> findExpiredQuotes(@Param("today") LocalDate today);

    @Query(value = "SELECT nextval('quote_number_seq')", nativeQuery = true)
    long nextQuoteSequence();
}
