package com.laulain.rentals.service;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.BookingDto;
import com.laulain.rentals.dto.QuoteContractDto.*;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final BookingRepository bookingRepository;
    private final AppProperties appProperties;

    // ============================================================
    //  CREATE QUOTE
    // ============================================================

    @Transactional
    public QuoteResponse generateQuote(UUID bookingId, String notes) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        // Build quote from booking's pricing
        String quoteNumber = generateQuoteNumber();
        LocalDate validUntil = LocalDate.now().plusDays(appProperties.business().quoteExpiryDays());

        Quote quote = Quote.builder()
                .booking(booking)
                .quoteNumber(quoteNumber)
                .status(Quote.Status.DRAFT)
                .subtotal(booking.getSubtotal())
                .taxAmount(booking.getTaxAmount())
                .deliveryFee(booking.getDeliveryFee())
                .setupFee(booking.getSetupFee())
                .totalAmount(booking.getTotalAmount())
                .depositAmount(booking.getDepositAmount())
                .notes(notes)
                .validUntil(validUntil)
                .build();

        // PDF generation disabled for initial deployment
        Quote saved = quoteRepository.save(quote);

        // Advance booking status
        if (booking.getStatus() == Booking.Status.PENDING) {
            booking.setStatus(Booking.Status.QUOTE_SENT);
            bookingRepository.save(booking);
        }

        log.info("Generated quote {} for booking {}", quoteNumber, booking.getBookingNumber());
        return toQuoteResponse(saved);
    }

    // ============================================================
    //  SEND QUOTE
    // ============================================================

    @Transactional
    public QuoteResponse sendQuote(UUID quoteId) {
        Quote quote = getQuoteEntity(quoteId);

        if (quote.isExpired()) {
            throw new IllegalStateException("Cannot send an expired quote.");
        }

        quote.setStatus(Quote.Status.SENT);
        quote.setSentAt(Instant.now());
        Quote saved = quoteRepository.save(quote);

        log.info("[EMAIL STUB] Quote {} marked SENT — email disabled for initial deployment",
                quote.getQuoteNumber());

        return toQuoteResponse(saved);
    }

    // ============================================================
    //  QUOTE VIEWED — Called when customer opens the quote link
    // ============================================================

    @Transactional
    public QuoteResponse markViewed(UUID quoteId) {
        Quote quote = getQuoteEntity(quoteId);
        if (quote.getStatus() == Quote.Status.SENT) {
            quote.setStatus(Quote.Status.VIEWED);
            quote.setViewedAt(Instant.now());
            quoteRepository.save(quote);
        }
        return toQuoteResponse(quote);
    }

    // ============================================================
    //  QUOTE ACCEPTED — Customer accepts quote → triggers contract creation
    // ============================================================

    @Transactional
    public QuoteResponse acceptQuote(UUID quoteId) {
        Quote quote = getQuoteEntity(quoteId);

        if (!quote.isActionable()) {
            throw new IllegalStateException("This quote cannot be accepted in its current state: "
                    + quote.getStatus());
        }
        if (quote.isExpired()) {
            throw new IllegalStateException("This quote has expired. Please contact us for a new quote.");
        }

        quote.setStatus(Quote.Status.ACCEPTED);
        quote.setAcceptedAt(Instant.now());
        quoteRepository.save(quote);

        log.info("Quote {} accepted for booking {}", quote.getQuoteNumber(),
                quote.getBooking().getBookingNumber());

        return toQuoteResponse(quote);
    }

    // ============================================================
    //  REGENERATE PDF
    // ============================================================

    @Transactional
    public QuoteResponse regeneratePdf(UUID quoteId) {
        Quote quote = getQuoteEntity(quoteId);
        log.info("[PDF STUB] regeneratePdf called for {} — PDF generation disabled",
                quote.getQuoteNumber());
        return toQuoteResponse(quote);
    }

    // ============================================================
    //  QUERIES
    // ============================================================

    public QuoteResponse getQuoteById(UUID id) {
        return toQuoteResponse(getQuoteEntity(id));
    }

    public List<QuoteResponse> getQuotesForBooking(UUID bookingId) {
        return quoteRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)
                .stream().map(this::toQuoteResponse).collect(Collectors.toList());
    }

    public List<QuoteSummary> getQuotesExpiringSoon() {
        LocalDate warningDate = LocalDate.now().plusDays(2);
        return quoteRepository.findQuotesExpiringSoon(LocalDate.now(), warningDate)
                .stream().map(this::toQuoteSummary).collect(Collectors.toList());
    }

    // ============================================================
    //  SCHEDULER — Expire overdue quotes nightly
    // ============================================================

    @Scheduled(cron = "0 0 1 * * *", zone = "America/Chicago")
    @Transactional
    public void expireOverdueQuotes() {
        List<Quote> expired = quoteRepository.findExpiredQuotes(LocalDate.now());
        expired.forEach(q -> {
            q.setStatus(Quote.Status.EXPIRED);
            quoteRepository.save(q);
            log.info("Expired quote {}", q.getQuoteNumber());
        });
        if (!expired.isEmpty()) {
            log.info("Expired {} overdue quote(s)", expired.size());
        }
    }

    // ============================================================
    //  MAPPING
    // ============================================================

    private QuoteResponse toQuoteResponse(Quote q) {
        List<BookingDto.BookingLineItemResponse> lineItems =
                q.getBooking().getBookingItems().stream().map(li ->
                    BookingDto.BookingLineItemResponse.builder()
                        .id(li.getId())
                        .itemId(li.getItem() != null ? li.getItem().getId() : null)
                        .itemName(li.getItemName())
                        .quantity(li.getQuantity())
                        .unitPrice(li.getUnitPrice())
                        .lineTotal(li.getLineTotal())
                        .rentalDays(li.getRentalDays())
                        .build()
                ).collect(Collectors.toList());

        Booking b = q.getBooking();
        return QuoteResponse.builder()
                .id(q.getId())
                .quoteNumber(q.getQuoteNumber())
                .status(q.getStatus())
                .statusLabel(q.getStatus().name().charAt(0)
                        + q.getStatus().name().substring(1).toLowerCase())
                .bookingId(b.getId())
                .bookingNumber(b.getBookingNumber())
                .customerName(b.getCustomer() != null ? b.getCustomer().getFullName() : "")
                .customerEmail(b.getCustomer() != null ? b.getCustomer().getEmail() : "")
                .eventDate(b.getEventDate())
                .eventType(b.getEventType())
                .subtotal(q.getSubtotal())
                .deliveryFee(q.getDeliveryFee())
                .setupFee(q.getSetupFee())
                .taxAmount(q.getTaxAmount())
                .totalAmount(q.getTotalAmount())
                .depositAmount(q.getDepositAmount())
                .balanceDue(q.getBalanceDue())
                .lineItems(lineItems)
                .notes(q.getNotes())
                .validUntil(q.getValidUntil())
                .expired(q.isExpired())
                .actionable(q.isActionable())
                .pdfUrl(null)
                .sentAt(q.getSentAt())
                .viewedAt(q.getViewedAt())
                .acceptedAt(q.getAcceptedAt())
                .createdAt(q.getCreatedAt())
                .build();
    }

    private QuoteSummary toQuoteSummary(Quote q) {
        return QuoteSummary.builder()
                .id(q.getId())
                .quoteNumber(q.getQuoteNumber())
                .status(q.getStatus())
                .statusLabel(q.getStatus().name())
                .bookingNumber(q.getBooking().getBookingNumber())
                .customerName(q.getBooking().getCustomer() != null
                        ? q.getBooking().getCustomer().getFullName() : "")
                .eventDate(q.getBooking().getEventDate())
                .totalAmount(q.getTotalAmount())
                .validUntil(q.getValidUntil())
                .expired(q.isExpired())
                .createdAt(q.getCreatedAt())
                .build();
    }

    private Quote getQuoteEntity(UUID id) {
        return quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + id));
    }

    private String generateQuoteNumber() {
        long seq = quoteRepository.nextQuoteSequence();
        return String.format("LLR-Q-%d-%04d", Year.now().getValue(), seq);
    }
}
