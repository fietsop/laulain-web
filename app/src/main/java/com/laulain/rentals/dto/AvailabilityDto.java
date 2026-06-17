package com.laulain.rentals.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for Availability Calendar API.
 * These are consumed by FullCalendar.js on the frontend.
 */
public class AvailabilityDto {

    /**
     * FullCalendar event object — represents a booking block on the calendar.
     * Matches the FullCalendar Event Object specification.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarEvent {
        private String id;
        private String title;
        private String start;        // ISO date: "2024-06-15"
        private String end;          // ISO date (exclusive in FullCalendar): "2024-06-16"
        private String color;
        private String textColor;
        private Boolean allDay;
        private CalendarEventExtended extendedProps;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarEventExtended {
        private String bookingNumber;
        private String customerName;
        private String status;
        private String itemName;
        private UUID itemId;
        private UUID bookingId;
    }

    /**
     * Availability check request — sent from the booking form.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityCheckRequest {
        private UUID itemId;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer quantity;
    }

    /**
     * Availability check response — returned to the booking form / public calendar.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityCheckResponse {
        private UUID itemId;
        private String itemName;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean available;
        private int requestedQuantity;
        private int availableQuantity;
        private int stockQuantity;
        private List<LocalDate> unavailableDates;   // Specific dates that are blocked
        private String message;
    }

    /**
     * Per-item availability summary for a date range.
     * Used to build the public catalog availability indicators.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemAvailabilitySummary {
        private UUID itemId;
        private String itemName;
        private BigDecimal pricePerDay;
        private int stockQuantity;
        private Map<String, Integer> availabilityByDate; // "2024-06-15" → available qty
    }

    /**
     * Monthly calendar data for the public-facing item calendar.
     * Shows which dates are fully booked, partially booked, or available.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyAvailability {
        private int year;
        private int month;
        private List<DayAvailability> days;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayAvailability {
        private LocalDate date;
        private DayStatus status;          // AVAILABLE | PARTIAL | FULLY_BOOKED | PAST
        private int availableQuantity;
        private int totalQuantity;
        private boolean isBufferDay;       // Buffer day around another booking

        public enum DayStatus {
            AVAILABLE,      // All units free
            PARTIAL,        // Some units booked, some free
            FULLY_BOOKED,   // No units available
            PAST,           // Date is in the past
            MIN_ADVANCE     // Too close to today (min advance booking rule)
        }
    }

    /**
     * Admin-wide calendar event list — all bookings across all items.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminCalendarResponse {
        private List<CalendarEvent> events;
        private List<String> fullyBookedDates;    // Dates with ALL items at capacity
        private int totalBookingsInRange;
    }
}
