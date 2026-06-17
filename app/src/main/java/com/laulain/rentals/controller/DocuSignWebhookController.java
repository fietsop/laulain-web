package com.laulain.rentals.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laulain.rentals.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives DocuSign Connect webhooks.
 *
 * Configure in DocuSign Admin → Connect → Add Configuration:
 *   URL: https://www.laulainluxerentals.com/docusign/webhook
 *   Events: envelope-completed, envelope-declined, envelope-voided
 *   Include: envelope data
 */
@RestController
@RequestMapping("/docusign")
@RequiredArgsConstructor
@Slf4j
public class DocuSignWebhookController {

    private final ContractService contractService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            // DocuSign Connect sends XML-like JSON payload
            String envelopeId = extractField(root, "envelopeId");
            String status     = extractField(root, "status");

            if (envelopeId == null || status == null) {
                log.warn("DocuSign webhook received with missing fields: {}", payload);
                return ResponseEntity.badRequest().body("Missing required fields");
            }

            log.info("DocuSign webhook: envelopeId={} status={}", envelopeId, status);
            contractService.handleDocuSignWebhook(envelopeId, status);

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing DocuSign webhook", e);
            // Return 200 to DocuSign to prevent retries — log and handle manually
            return ResponseEntity.ok("Processed with errors");
        }
    }

    private String extractField(JsonNode root, String field) {
        // DocuSign wraps payload: data.envelopeSummary.envelopeId
        JsonNode data = root.path("data");
        if (!data.isMissingNode()) {
            JsonNode envelope = data.path("envelopeSummary");
            if (!envelope.isMissingNode()) {
                JsonNode node = envelope.path(field);
                if (!node.isMissingNode()) return node.asText();
            }
        }
        // Fall back to top-level field
        JsonNode node = root.path(field);
        return node.isMissingNode() ? null : node.asText();
    }
}
