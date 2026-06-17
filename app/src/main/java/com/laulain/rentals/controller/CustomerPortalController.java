package com.laulain.rentals.controller;

import com.laulain.rentals.dto.QuoteContractDto.*;
import com.laulain.rentals.service.ContractService;
import com.laulain.rentals.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Customer-facing quote and contract portal.
 * Accessible to CUSTOMER and ADMIN roles.
 */
@Controller
@RequestMapping("/portal")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final QuoteService quoteService;
    private final ContractService contractService;

    // ---- View a quote ----
    @GetMapping("/quotes/{id}")
    public String viewQuote(@PathVariable UUID id, Model model) {
        QuoteResponse quote = quoteService.markViewed(id);  // Marks as VIEWED on open
        model.addAttribute("quote", quote);
        model.addAttribute("pageTitle", "Your Quote — " + quote.getQuoteNumber());
        return "portal/quote-detail";
    }

    // ---- Accept a quote ----
    @PostMapping("/quotes/{id}/accept")
    public String acceptQuote(
            @PathVariable UUID id,
            RedirectAttributes redirectAttrs
    ) {
        quoteService.acceptQuote(id);
        redirectAttrs.addFlashAttribute("successMessage",
                "Quote accepted! Your contract is being prepared and will be sent for signature shortly.");
        return "redirect:/portal/quotes/" + id;
    }

    // ---- View contract ----
    @GetMapping("/contracts/{id}")
    public String viewContract(
            @PathVariable UUID id,
            @RequestParam(required = false) String signed,
            Model model
    ) {
        ContractResponse contract = contractService.getContractById(id);

        if ("true".equals(signed)) {
            model.addAttribute("justSigned", true);
        }

        model.addAttribute("contract", contract);
        model.addAttribute("pageTitle", "Your Contract — " + contract.getContractNumber());
        return "portal/contract-detail";
    }
}
