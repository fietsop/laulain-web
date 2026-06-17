package com.laulain.rentals.controller;

import com.laulain.rentals.dto.BookingDto.BookingSummary;
import com.laulain.rentals.repository.CustomerRepository;
import com.laulain.rentals.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Customer portal — booking history dashboard.
 */
@Controller
@RequestMapping("/portal")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PortalDashboardController {

    private final BookingService bookingService;
    private final CustomerRepository customerRepository;

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        String username = userDetails.getUsername();

        // Look up customer by email (username = email or login name)
        UUID customerId = customerRepository.findByEmail(username)
                .map(c -> c.getId())
                .orElse(null);

        Page<BookingSummary> bookings = customerId != null
                ? bookingService.getBookingsForCustomer(customerId, page, 10)
                : Page.empty();

        model.addAttribute("bookings", bookings.getContent());
        model.addAttribute("totalPages", bookings.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("customerName", username);
        model.addAttribute("pageTitle", "My Bookings — Laulain Luxe Rentals");
        return "portal/dashboard";
    }
}
