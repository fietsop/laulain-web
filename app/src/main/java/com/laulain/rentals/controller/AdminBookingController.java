package com.laulain.rentals.controller;

import com.laulain.rentals.dto.BookingDto.*;
import com.laulain.rentals.model.Booking;
import com.laulain.rentals.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminBookingController {

    private final BookingService bookingService;

    /** Booking list with filters */
    @GetMapping
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Booking.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = Booking.Status.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        model.addAttribute("bookings",
                bookingService.getBookingsForAdmin(statusEnum, eventDate, search, page, 20));
        model.addAttribute("statuses", Booking.Status.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDate", eventDate);
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageTitle", "Manage Bookings");
        return "admin/bookings/list";
    }

    /** Booking detail */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        BookingResponse booking = bookingService.getBookingById(id);
        model.addAttribute("booking", booking);
        model.addAttribute("statuses", Booking.Status.values());
        model.addAttribute("pageTitle", "Booking " + booking.getBookingNumber());
        return "admin/bookings/detail";
    }

    /** Confirm booking (admin manual confirm) */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable UUID id, RedirectAttributes redirectAttrs) {
        bookingService.confirmBooking(id);
        redirectAttrs.addFlashAttribute("successMessage", "Booking confirmed and availability blocks created.");
        return "redirect:/admin/bookings/" + id;
    }

    /** Cancel booking */
    @PostMapping("/{id}/cancel")
    public String cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttrs
    ) {
        bookingService.cancelBooking(id, reason);
        redirectAttrs.addFlashAttribute("successMessage", "Booking cancelled.");
        return "redirect:/admin/bookings/" + id;
    }

    /** Update admin notes / status */
    @PostMapping("/{id}/update")
    public String update(
            @PathVariable UUID id,
            @ModelAttribute AdminBookingUpdate update,
            RedirectAttributes redirectAttrs
    ) {
        bookingService.updateAdminNotes(id, update);
        redirectAttrs.addFlashAttribute("successMessage", "Booking updated.");
        return "redirect:/admin/bookings/" + id;
    }

    /** REST endpoint — booking detail as JSON (for modal/AJAX) */
    @GetMapping("/{id}/json")
    @ResponseBody
    public ResponseEntity<BookingResponse> detailJson(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }
}
