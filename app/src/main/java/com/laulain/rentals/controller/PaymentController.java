package com.laulain.rentals.controller;

import com.laulain.rentals.dto.PaymentDto.*;
import com.laulain.rentals.service.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Payment controller — customer checkout flow and Stripe webhook.
 *
 * Public (no auth):
 *   POST /stripe/webhook       — Stripe sends events here
 *
 * Customer-authenticated:
 *   POST /portal/payments/deposit/{bookingId}   — Start deposit checkout
 *   POST /portal/payments/balance/{bookingId}   — Start balance checkout
 *   GET  /portal/payments/success               — Post-payment success page
 *   GET  /portal/payments/cancel                — Post-payment cancel page
 *   GET  /portal/payments/booking/{bookingId}   — Payment history for a booking (JSON)
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final StripePaymentService paymentService;

    // ============================================================
    //  STRIPE WEBHOOK — No auth, no CSRF (raw body required)
    // ============================================================

    @PostMapping("/stripe/webhook")
    @ResponseBody
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            paymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("OK");
        } catch (SecurityException e) {
            log.warn("Stripe webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            log.error("Stripe webhook processing error", e);
            // Return 200 to prevent Stripe retries on internal errors
            return ResponseEntity.ok("Accepted");
        }
    }

    // ============================================================
    //  CUSTOMER PORTAL — Payment initiation
    // ============================================================

    /** Initiate deposit payment — redirects to Stripe Checkout */
    @PostMapping("/portal/payments/deposit/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public String initiateDeposit(
            @PathVariable UUID bookingId,
            Model model
    ) {
        try {
            CheckoutSessionResponse session = paymentService.createDepositCheckout(bookingId);
            return "redirect:" + session.getCheckoutUrl();
        } catch (Exception e) {
            log.error("Failed to create deposit checkout for booking {}", bookingId, e);
            model.addAttribute("errorMessage", "Unable to initiate payment: " + e.getMessage());
            return "portal/payment-error";
        }
    }

    /** Initiate balance payment — redirects to Stripe Checkout */
    @PostMapping("/portal/payments/balance/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public String initiateBalance(
            @PathVariable UUID bookingId,
            Model model
    ) {
        try {
            CheckoutSessionResponse session = paymentService.createBalanceCheckout(bookingId);
            return "redirect:" + session.getCheckoutUrl();
        } catch (Exception e) {
            log.error("Failed to create balance checkout for booking {}", bookingId, e);
            model.addAttribute("errorMessage", "Unable to initiate payment: " + e.getMessage());
            return "portal/payment-error";
        }
    }

    /** Stripe redirects here after successful payment */
    @GetMapping("/portal/payments/success")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public String paymentSuccess(
            @RequestParam(required = false) String session_id,
            @RequestParam(required = false) UUID paymentId,
            Model model
    ) {
        if (paymentId != null) {
            try {
                PaymentResponse payment = paymentService.getPaymentById(paymentId);
                model.addAttribute("payment", payment);
            } catch (Exception e) {
                log.warn("Could not load payment {} for success page", paymentId);
            }
        }
        model.addAttribute("pageTitle", "Payment Successful");
        return "portal/payment-success";
    }

    /** Stripe redirects here if customer cancels checkout */
    @GetMapping("/portal/payments/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public String paymentCancel(
            @RequestParam(required = false) UUID bookingId,
            Model model
    ) {
        model.addAttribute("bookingId", bookingId);
        model.addAttribute("pageTitle", "Payment Cancelled");
        return "portal/payment-cancel";
    }

    /** Payment history for a booking (JSON) — used by portal and admin */
    @GetMapping("/portal/payments/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @ResponseBody
    public ResponseEntity<?> getBookingPayments(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(paymentService.getPaymentsForBooking(bookingId));
    }

    // ============================================================
    //  ADMIN — Refunds and payment management
    // ============================================================

    @PostMapping("/admin/payments/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<PaymentResponse> issueRefund(@RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.issueRefund(request));
    }

    @GetMapping("/admin/payments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/admin/payments/revenue-summary")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<RevenueSummary> getRevenueSummary() {
        return ResponseEntity.ok(paymentService.getRevenueSummary());
    }
}
