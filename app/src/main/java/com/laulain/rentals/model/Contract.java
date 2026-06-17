package com.laulain.rentals.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contracts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contract {

    public enum Status {
        PENDING_SIGNATURE,
        SIGNED,
        VOIDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "contract_number", nullable = false, unique = true, length = 20)
    private String contractNumber;   // LLR-C-2024-0001

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.PENDING_SIGNATURE;

    @Column(name = "docusign_envelope_id", length = 255)
    private String docusignEnvelopeId;

    @Column(name = "pdf_s3_key", length = 500)
    private String pdfS3Key;           // Unsigned contract PDF

    @Column(name = "signed_pdf_s3_key", length = 500)
    private String signedPdfS3Key;     // Signed contract PDF (from DocuSign)

    @Column(name = "customer_ip", length = 45)
    private String customerIp;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isSigned() { return status == Status.SIGNED; }
}
