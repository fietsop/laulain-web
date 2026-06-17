package com.laulain.rentals.dto;

import com.laulain.rentals.model.Contract;
import com.laulain.rentals.model.Quote;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class QuoteContractDto {

    // ============================================================
    //  QUOTE DTOs
    // ============================================================

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuoteResponse {
        private UUID id;
        private String quoteNumber;
        private Quote.Status status;
        private String statusLabel;

        // Booking context
        private UUID bookingId;
        private String bookingNumber;
        private String customerName;
        private String customerEmail;
        private LocalDate eventDate;
        private String eventType;

        // Pricing
        private BigDecimal subtotal;
        private BigDecimal deliveryFee;
        private BigDecimal setupFee;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private BigDecimal depositAmount;
        private BigDecimal balanceDue;

        // Line items
        private List<BookingDto.BookingLineItemResponse> lineItems;

        // Meta
        private String notes;
        private LocalDate validUntil;
        private boolean expired;
        private boolean actionable;
        private String pdfUrl;           // Presigned S3 URL
        private Instant sentAt;
        private Instant viewedAt;
        private Instant acceptedAt;
        private Instant createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuoteSummary {
        private UUID id;
        private String quoteNumber;
        private Quote.Status status;
        private String statusLabel;
        private String bookingNumber;
        private String customerName;
        private LocalDate eventDate;
        private BigDecimal totalAmount;
        private LocalDate validUntil;
        private boolean expired;
        private Instant createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class QuoteNoteRequest {
        private String notes;
    }

    // ============================================================
    //  CONTRACT DTOs
    // ============================================================

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ContractResponse {
        private UUID id;
        private String contractNumber;
        private Contract.Status status;
        private String statusLabel;

        // Context
        private UUID bookingId;
        private String bookingNumber;
        private UUID quoteId;
        private String quoteNumber;
        private String customerName;
        private String customerEmail;
        private LocalDate eventDate;

        // DocuSign
        private String docusignEnvelopeId;
        private String signingUrl;        // DocuSign embedded signing URL

        // PDFs
        private String unsignedPdfUrl;
        private String signedPdfUrl;

        // Timestamps
        private Instant signedAt;
        private Instant createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DocuSignWebhookPayload {
        private String envelopeId;
        private String status;            // "completed", "declined", "voided"
        private String completedDateTime;
    }
}
