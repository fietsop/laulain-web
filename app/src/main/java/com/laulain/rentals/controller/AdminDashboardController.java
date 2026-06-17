package com.laulain.rentals.controller;

import com.laulain.rentals.model.Booking;
import com.laulain.rentals.repository.BookingRepository;
import com.laulain.rentals.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final BookingRepository bookingRepository;
    private final QuoteService quoteService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Booking counts by status
        model.addAttribute("pendingCount",     bookingRepository.countByStatus(Booking.Status.PENDING));
        model.addAttribute("confirmedCount",   bookingRepository.countByStatus(Booking.Status.CONFIRMED));
        model.addAttribute("inProgressCount",  bookingRepository.countByStatus(Booking.Status.IN_PROGRESS));
        model.addAttribute("completedCount",   bookingRepository.countByStatus(Booking.Status.COMPLETED));

        // Revenue summary disabled for initial deployment
        model.addAttribute("revenue", null);

        // Quotes expiring soon (next 2 days)
        model.addAttribute("expiringQuotes", quoteService.getQuotesExpiringSoon());

        model.addAttribute("pageTitle", "Admin Dashboard");
        return "admin/dashboard";
    }
}
