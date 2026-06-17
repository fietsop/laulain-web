package com.laulain.rentals.service;

import com.laulain.rentals.model.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PdfGenerationService stub — iText PDF generation disabled for initial deployment.
 * Returns empty byte arrays. Re-enable full implementation when iText is added back.
 */
@Service
@Slf4j
public class PdfGenerationService {

    public byte[] generateQuotePdf(Quote quote) {
        log.info("[PDF STUB] generateQuotePdf called for {} — PDF generation disabled",
                quote.getQuoteNumber());
        return new byte[0];
    }

    public byte[] generateContractPdf(Quote quote, String contractNumber) {
        log.info("[PDF STUB] generateContractPdf called for {} — PDF generation disabled",
                contractNumber);
        return new byte[0];
    }
}
