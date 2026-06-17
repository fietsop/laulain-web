package com.laulain.rentals.service;

import com.laulain.rentals.config.AppProperties;
import com.laulain.rentals.dto.QuoteContractDto.*;
import com.laulain.rentals.exception.ResourceNotFoundException;
import com.laulain.rentals.model.*;
import com.laulain.rentals.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contractRepository;
    private final QuoteRepository quoteRepository;
    private final BookingRepository bookingRepository;
    private final DocuSignService docuSignService;
    private final BookingService bookingService;
    private final AppProperties appProperties;

    // ============================================================
    //  CREATE CONTRACT from accepted quote
    // ============================================================

    @Transactional
    public ContractResponse createContract(UUID quoteId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + quoteId));

        if (quote.getStatus() != Quote.Status.ACCEPTED) {
            throw new IllegalStateException(
                    "Contract can only be created from an ACCEPTED quote. Current status: " + quote.getStatus());
        }

        Booking booking = bookingRepository.findByIdWithDetails(quote.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        String contractNumber = generateContractNumber();

        // PDF generation disabled for initial deployment
        Contract contract = Contract.builder()
                .booking(booking)
                .quote(quote)
                .contractNumber(contractNumber)
                .status(Contract.Status.PENDING_SIGNATURE)
                .build();

        Contract saved = contractRepository.save(contract);
        log.info("Contract {} created (DocuSign disabled for initial deployment)", contractNumber);

        return toContractResponse(saved);
    }

    // ============================================================
    //  EMBEDDED SIGNING URL — Generate fresh signing URL
    // ============================================================

    public String getSigningUrl(UUID contractId, String returnUrl) {
        log.info("[DOCUSIGN STUB] getSigningUrl called for contract {} — DocuSign disabled", contractId);
        return null;
    }

    // ============================================================
    //  DOCUSIGN WEBHOOK — Called when customer signs or declines
    // ============================================================

    @Transactional
    public void handleDocuSignWebhook(String envelopeId, String status) {
        Contract contract = contractRepository.findByDocusignEnvelopeId(envelopeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No contract found for envelope: " + envelopeId));

        log.info("DocuSign webhook: envelope={} status={}", envelopeId, status);

        switch (status.toLowerCase()) {
            case "completed" -> handleContractSigned(contract);
            case "declined"  -> handleContractDeclined(contract);
            case "voided"    -> handleContractVoided(contract);
            default -> log.warn("Unhandled DocuSign status: {}", status);
        }
    }

    private void handleContractSigned(Contract contract) {
        // Signed PDF download disabled for initial deployment (DocuSign/S3 not configured)
        // 1. Mark contract as signed
        contract.setStatus(Contract.Status.SIGNED);
        contract.setSignedAt(Instant.now());
        contractRepository.save(contract);

        // 2. Confirm the booking — creates availability blocks
        bookingService.confirmBookingByPayment(contract.getBooking().getId());

        log.info("Contract {} signed — booking {} confirmed",
                contract.getContractNumber(), contract.getBooking().getBookingNumber());
    }

    private void handleContractDeclined(Contract contract) {
        contract.setStatus(Contract.Status.VOIDED);
        contract.setVoidedAt(Instant.now());
        contractRepository.save(contract);
        log.info("Contract {} declined by customer", contract.getContractNumber());
    }

    private void handleContractVoided(Contract contract) {
        contract.setStatus(Contract.Status.VOIDED);
        contract.setVoidedAt(Instant.now());
        contractRepository.save(contract);
        log.info("Contract {} voided", contract.getContractNumber());
    }

    // ============================================================
    //  VOID CONTRACT — Admin action
    // ============================================================

    @Transactional
    public ContractResponse voidContract(UUID contractId, String reason) {
        Contract contract = getContractEntity(contractId);

        if (contract.isSigned()) {
            throw new IllegalStateException("A signed contract cannot be voided.");
        }

        if (contract.getDocusignEnvelopeId() != null) {
            docuSignService.voidEnvelope(contract.getDocusignEnvelopeId(), reason);
        }

        contract.setStatus(Contract.Status.VOIDED);
        contract.setVoidedAt(Instant.now());
        return toContractResponse(contractRepository.save(contract));
    }

    // ============================================================
    //  QUERIES
    // ============================================================

    public ContractResponse getContractById(UUID id) {
        return toContractResponse(getContractEntity(id));
    }

    public List<ContractResponse> getContractsForBooking(UUID bookingId) {
        return contractRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)
                .stream().map(this::toContractResponse).collect(Collectors.toList());
    }

    // ============================================================
    //  MAPPING
    // ============================================================

    private ContractResponse toContractResponse(Contract c) {
        String unsignedUrl = null; // S3 disabled for initial deployment
        String signedUrl = null;   // S3 disabled for initial deployment

        Booking b = c.getBooking();
        return ContractResponse.builder()
                .id(c.getId())
                .contractNumber(c.getContractNumber())
                .status(c.getStatus())
                .statusLabel(c.getStatus().name().replace("_", " "))
                .bookingId(b.getId())
                .bookingNumber(b.getBookingNumber())
                .quoteId(c.getQuote().getId())
                .quoteNumber(c.getQuote().getQuoteNumber())
                .customerName(b.getCustomer() != null ? b.getCustomer().getFullName() : "")
                .customerEmail(b.getCustomer() != null ? b.getCustomer().getEmail() : "")
                .eventDate(b.getEventDate())
                .docusignEnvelopeId(c.getDocusignEnvelopeId())
                .unsignedPdfUrl(unsignedUrl)
                .signedPdfUrl(signedUrl)
                .signedAt(c.getSignedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private Contract getContractEntity(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + id));
    }

    private String generateContractNumber() {
        long seq = contractRepository.nextContractSequence();
        return String.format("LLR-C-%d-%04d", Year.now().getValue(), seq);
    }
}
