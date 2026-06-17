package com.laulain.rentals.service;

import com.laulain.rentals.model.Contract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DocuSignService stub — DocuSign e-signature disabled for initial deployment.
 * All methods are no-ops. Re-enable full implementation when DocuSign is configured.
 */
@Service
@Slf4j
public class DocuSignService {

    public String createAndSendEnvelope(Contract contract, byte[] contractPdfBytes) {
        log.info("[DOCUSIGN STUB] createAndSendEnvelope called for contract {} — DocuSign disabled",
                contract.getContractNumber());
        return null;
    }

    public String getEmbeddedSigningUrl(String envelopeId, String signerEmail,
                                         String signerName, String returnUrl) {
        log.info("[DOCUSIGN STUB] getEmbeddedSigningUrl called — DocuSign disabled");
        return null;
    }

    public byte[] downloadSignedPdf(String envelopeId) {
        log.info("[DOCUSIGN STUB] downloadSignedPdf called for envelope {} — DocuSign disabled",
                envelopeId);
        return new byte[0];
    }

    public void voidEnvelope(String envelopeId, String reason) {
        log.info("[DOCUSIGN STUB] voidEnvelope called for envelope {} — DocuSign disabled",
                envelopeId);
    }
}
