package com.laulain.rentals.controller;

import com.laulain.rentals.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the admin calendar HTML page.
 * Actual calendar data is fetched via AJAX by FullCalendar from /api/admin/calendar/events.
 */
@Controller
@RequestMapping("/admin/calendar")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final ItemRepository itemRepository;

    @GetMapping
    public String calendar(Model model) {
        model.addAttribute("items", itemRepository.findByActiveTrueOrderBySortOrderAscNameAsc());
        model.addAttribute("pageTitle", "Availability Calendar");
        return "admin/calendar/index";
    }
}
