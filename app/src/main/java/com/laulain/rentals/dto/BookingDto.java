package com.laulain.rentals.dto;

import com.laulain.rentals.model.Booking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class BookingDto {

    // ============================================================
    //  REQUEST — Booking form submission
    // ============================================================

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class BookingRequest {

        // ---- Customer info (pre-filled from Cognito if logged in) ----
        @NotBlank(message = "First name is required")
        @Size(max = 100)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        private String email;

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[\\d\\s\\-\\(\\)\\+]{7,20}$", message = "Invalid phone number")
        private String phone;

        // ---- Event details ----
        @NotNull(message = "Event date is required")
        @Future(message = "Event date must be in the future")
        private LocalDate eventDate;

        private LocalTime eventStartTime;
        private LocalTime eventEndTime;

        @NotBlank(message = "Event type is required")
        @Size(max = 100)
        private String eventType;

        @Size(max = 255)
        private String eventVenue;

        @Min(value = 1, message = "Guest count must be at least 1")
        @Max(value = 10000)
        private Integer guestCount;

        // ---- Delivery details ----
        @NotBlank(message = "Delivery address is required")
        @Size(max = 255)
        private String deliveryAddressLine1;

        @Size(max = 100)
        private String deliveryAddressLine2;

        @NotBlank(message = "City is required")
        @Size(max = 100)
        private String deliveryCity;

        @NotBlank(message = "State is required")
        private String deliveryState;

        @NotBlank(message = "ZIP code is required")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Invalid ZIP code")
        private String deliveryZip;

        private Boolean requiresSetup = false;

        // ---- Items requested ----
        @NotEmpty(message = "Please add at least one item to your booking")
        @Valid
        private List<BookingItemRequest> items;

        @Size(max = 2000)
        private String customerNotes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class BookingItemRequest {
        @NotNull(message = "Item ID is required")
        private UUID itemId;

        @NotNull
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }

    // ============================================================
    //  RESPONSE — Booking detail / list
    // ============================================================

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingResponse {
        private UUID id;
        private String bookingNumber;
        private Booking.Status status;
        private String statusLabel;
        private String statusColor;

        // Customer
        private String customerName;
        private String customerEmail;
        private String customerPhone;

        // Event
        private LocalDate eventDate;
        private LocalTime eventStartTime;
        private String eventType;
        private String eventVenue;
        private Integer guestCount;

        // Delivery
        private String deliveryAddress;
        private BigDecimal deliveryFee;
        private Boolean requiresSetup;
        private BigDecimal setupFee;

        // Pricing
        private BigDecimal subtotal;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private BigDecimal depositAmount;
        private BigDecimal balanceDue;

        // Line items
        private List<BookingLineItemResponse> items;

        // Notes
        private String customerNotes;
        private String adminNotes;

        // Timestamps
        private Instant createdAt;
        private Instant confirmedAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingLineItemResponse {
        private UUID id;
        private UUID itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private Integer rentalDays;
    }

    /** Compact summary for list views */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingSummary {
        private UUID id;
        private String bookingNumber;
        private String customerName;
        private LocalDate eventDate;
        private String eventType;
        private Booking.Status status;
        private String statusLabel;
        private String statusColor;
        private BigDecimal totalAmount;
        private BigDecimal depositAmount;
        private Instant createdAt;
    }

    /** Admin update form — notes, status change */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class AdminBookingUpdate {
        private String adminNotes;
        private Booking.Status status;
    }

    /** Pricing estimate — returned after item selection, before form submission */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PricingEstimate {
        private List<LineItemEstimate> lineItems;
        private BigDecimal subtotal;
        private BigDecimal deliveryFee;
        private BigDecimal setupFee;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private BigDecimal depositAmount;
        private BigDecimal balanceDue;
        private double taxRate;
        private int depositPercentage;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LineItemEstimate {
        private UUID itemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private Integer rentalDays;
        private BigDecimal lineTotal;
    }
}
