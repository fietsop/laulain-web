package com.laulain.rentals.controller;

import com.laulain.rentals.dto.AvailabilityDto.*;
import com.laulain.rentals.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * REST API for availability data — consumed by FullCalendar.js and the booking form.
 *
 * Public endpoints:
 *   GET  /api/availability/check          — check specific item + date range
 *   GET  /api/availability/item/{id}/month — monthly calendar data for one item
 *   POST /api/availability/check-multiple  — check multiple items at once
 *
 * Admin endpoints:
 *   GET  /api/admin/calendar/events        — all bookings for admin calendar
 *   POST /api/admin/availability/block     — create manual maintenance block
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    // ============================================================
    //  PUBLIC API — No authentication required
    // ============================================================

    /**
     * Check if a single item is available for given dates and quantity.
     * Called via AJAX from the booking form as the user selects dates.
     */
    @GetMapping("/api/availability/check")
    public ResponseEntity<AvailabilityCheckResponse> checkAvailability(
            @RequestParam UUID itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int quantity
    ) {
        AvailabilityCheckRequest request = new AvailabilityCheckRequest(
                itemId, startDate, endDate, quantity);
        return ResponseEntity.ok(availabilityService.checkAvailability(request));
    }

    /**
     * Check availability for multiple items at once.
     * Used when the cart has multiple items — validates all before proceeding.
     */
    @PostMapping("/api/availability/check-multiple")
    public ResponseEntity<List<AvailabilityCheckResponse>> checkMultiple(
            @RequestBody List<AvailabilityCheckRequest> requests
    ) {
        return ResponseEntity.ok(availabilityService.checkMultipleItems(requests));
    }

    /**
     * Monthly availability for a specific item — used by the public item calendar.
     * Returns day-by-day status: AVAILABLE / PARTIAL / FULLY_BOOKED / PAST.
     */
    @GetMapping("/api/availability/item/{itemId}/month")
    public ResponseEntity<MonthlyAvailability> getMonthlyAvailability(
            @PathVariable UUID itemId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month)
                : YearMonth.now();

        return ResponseEntity.ok(
                availabilityService.getMonthlyAvailability(itemId, ym.getYear(), ym.getMonthValue())
        );
    }

    // ============================================================
    //  ADMIN API — Requires ADMIN role
    // ============================================================

    /**
     * All booking events for the admin FullCalendar.
     * FullCalendar calls this with its current view's start/end dates.
     */
    @GetMapping("/api/admin/calendar/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCalendarResponse> getAdminCalendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(availabilityService.getAdminCalendarEvents(start, end));
    }

    /**
     * Manually block an item for maintenance or hold.
     * Admin only — bypasses the booking workflow.
     */
    @PostMapping("/api/admin/availability/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> createManualBlock(
            @RequestParam UUID itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "MAINTENANCE") String reason
    ) {
        availabilityService.createManualBlock(itemId, startDate, endDate, reason);
        return ResponseEntity.ok().build();
    }
}
