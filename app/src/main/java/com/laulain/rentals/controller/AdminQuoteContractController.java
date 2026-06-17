package com.laulain.rentals.controller;

import com.laulain.rentals.dto.QuoteContractDto.*;
import com.laulain.rentals.service.ContractService;
import com.laulain.rentals.service.QuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Admin controller for quote and contract management.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminQuoteContractController {

    private final QuoteService quoteService;
    private final ContractService contractService;

    // ============================================================
    //  QUOTES
    // ============================================================

    /** Generate a quote for a booking */
    @PostMapping("/admin/bookings/{bookingId}/quotes")
    public String generateQuote(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttrs
    ) {
        QuoteResponse quote = quoteService.generateQuote(bookingId, notes);
        redirectAttrs.addFlashAttribute("successMessage",
                "Quote " + quote.getQuoteNumber() + " generated successfully.");
        return "redirect:/admin/bookings/" + bookingId;
    }

    /** View a quote */
    @GetMapping("/admin/quotes/{id}")
    public String viewQuote(@PathVariable UUID id, Model model) {
        QuoteResponse quote = quoteService.getQuoteById(id);
        model.addAttribute("quote", quote);
        model.addAttribute("pageTitle", "Quote " + quote.getQuoteNumber());
        return "admin/quotes/detail";
    }

    /** Send a quote to the customer */
    @PostMapping("/admin/quotes/{id}/send")
    public String sendQuote(@PathVariable UUID id, RedirectAttributes redirectAttrs) {
        QuoteResponse quote = quoteService.sendQuote(id);
        redirectAttrs.addFlashAttribute("successMessage",
                "Quote " + quote.getQuoteNumber() + " sent to " + quote.getCustomerEmail());
        return "redirect:/admin/quotes/" + id;
    }

    /** Regenerate quote PDF */
    @PostMapping("/admin/quotes/{id}/regenerate-pdf")
    public String regeneratePdf(@PathVariable UUID id, RedirectAttributes redirectAttrs) {
        quoteService.regeneratePdf(id);
        redirectAttrs.addFlashAttribute("successMessage", "PDF regenerated.");
        return "redirect:/admin/quotes/" + id;
    }

    /** Get quotes for a booking (JSON) */
    @GetMapping("/admin/bookings/{bookingId}/quotes/json")
    @ResponseBody
    public ResponseEntity<?> getQuotesForBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(quoteService.getQuotesForBooking(bookingId));
    }

    // ============================================================
    //  CONTRACTS
    // ============================================================

    /** Create contract from accepted quote */
    @PostMapping("/admin/quotes/{quoteId}/contracts")
    public String createContract(
            @PathVariable UUID quoteId,
            RedirectAttributes redirectAttrs
    ) {
        ContractResponse contract = contractService.createContract(quoteId);
        redirectAttrs.addFlashAttribute("successMessage",
                "Contract " + contract.getContractNumber() + " created and sent for signature.");
        return "redirect:/admin/contracts/" + contract.getId();
    }

    /** View a contract */
    @GetMapping("/admin/contracts/{id}")
    public String viewContract(@PathVariable UUID id, Model model) {
        ContractResponse contract = contractService.getContractById(id);
        model.addAttribute("contract", contract);
        model.addAttribute("pageTitle", "Contract " + contract.getContractNumber());
        return "admin/contracts/detail";
    }

    /** Void a contract */
    @PostMapping("/admin/contracts/{id}/void")
    public String voidContract(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Voided by admin") String reason,
            RedirectAttributes redirectAttrs
    ) {
        ContractResponse contract = contractService.voidContract(id, reason);
        redirectAttrs.addFlashAttribute("successMessage", "Contract voided.");
        return "redirect:/admin/contracts/" + id;
    }
}
