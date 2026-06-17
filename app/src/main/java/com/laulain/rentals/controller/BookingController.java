package com.laulain.rentals.controller;

import com.laulain.rentals.dto.AvailabilityDto.AvailabilityCheckRequest;
import com.laulain.rentals.dto.AvailabilityDto.AvailabilityCheckResponse;
import com.laulain.rentals.dto.BookingDto.*;
import com.laulain.rentals.exception.BookingConflictException;
import com.laulain.rentals.service.AvailabilityService;
import com.laulain.rentals.service.BookingService;
import com.laulain.rentals.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public booking flow — accessible to guests and authenticated customers.
 */
@Controller
@RequestMapping("/booking")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final ItemService itemService;
    private final AvailabilityService availabilityService;

    /**
     * Step 1 — Booking form.
     * Pre-selects item if itemId + eventDate are provided (from catalog detail page).
     */
    @GetMapping("/new")
    public String newBookingForm(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String eventDate,
            @AuthenticationPrincipal UserDetails principal,
            Model model
    ) {
        BookingRequest form = new BookingRequest();

        // Pre-fill email from logged-in user
        if (principal != null) {
            form.setEmail(principal.getUsername());
        }

        model.addAttribute("form", form);
        model.addAttribute("categories", itemService.getActiveCategories());
        model.addAttribute("allItems", itemService.getAllActiveItems());
        model.addAttribute("preSelectedItemId", itemId);
        model.addAttribute("preSelectedDate", eventDate);
        model.addAttribute("isAuthenticated", principal != null);
        model.addAttribute("pageTitle", "Request a Booking — Laulain Luxe Rentals");
        return "booking/form";
    }

    /**
     * Step 2 — Submit booking form.
     */
    @PostMapping
    public String submitBooking(
            @Valid @ModelAttribute("form") BookingRequest request,
            BindingResult result,
            @AuthenticationPrincipal UserDetails principal,
            Model model,
            RedirectAttributes redirectAttrs
    ) {
        if (result.hasErrors()) {
            model.addAttribute("categories", itemService.getActiveCategories());
            model.addAttribute("allItems", itemService.getAllActiveItems());
            model.addAttribute("isAuthenticated", principal != null);
            return "booking/form";
        }

        try {
            BookingResponse booking = bookingService.createBooking(request, null);
            redirectAttrs.addFlashAttribute("booking", booking);
            return "redirect:/booking/confirmation/" + booking.getBookingNumber();
        } catch (BookingConflictException e) {
            model.addAttribute("availabilityError", e.getMessage());
            model.addAttribute("categories", itemService.getActiveCategories());
            model.addAttribute("allItems", itemService.getAllActiveItems());
            model.addAttribute("isAuthenticated", principal != null);
            return "booking/form";
        }
    }

    /**
     * Step 3 — Booking confirmation page.
     */
    @GetMapping("/confirmation/{bookingNumber}")
    public String confirmation(
            @PathVariable String bookingNumber,
            @ModelAttribute("booking") BookingResponse flashBooking,
            Model model
    ) {
        BookingResponse booking = (flashBooking != null && flashBooking.getBookingNumber() != null)
                ? flashBooking
                : bookingService.getBookingByNumber(bookingNumber);

        model.addAttribute("booking", booking);
        model.addAttribute("pageTitle", "Booking Received — " + bookingNumber);
        return "booking/confirmation";
    }

    // ============================================================
    //  AJAX API
    // ============================================================

    /** Live pricing estimate — called as the customer selects items */
    @PostMapping("/api/pricing-estimate")
    @ResponseBody
    public ResponseEntity<PricingEstimate> getPricingEstimate(
            @RequestBody List<BookingItemRequest> items,
            @RequestParam(defaultValue = "1") int rentalDays,
            @RequestParam(defaultValue = "false") boolean requiresSetup
    ) {
        return ResponseEntity.ok(
                bookingService.calculatePricingEstimate(items, rentalDays, requiresSetup));
    }

    /** Quick availability check from booking form */
    @GetMapping("/check-availability")
    @ResponseBody
    public ResponseEntity<AvailabilityCheckResponse> checkAvailability(
            @RequestParam UUID itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(defaultValue = "1") int quantity
    ) {
        AvailabilityCheckRequest request = new AvailabilityCheckRequest(
                itemId, eventDate, eventDate, quantity);
        return ResponseEntity.ok(availabilityService.checkAvailability(request));
    }
}
