package com.laulain.rentals.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for Item Catalog — separates API/form layer from JPA entities.
 */
public class ItemDto {

    // ============================================================
    //  RESPONSE DTOs (entity → view/API)
    // ============================================================

    /** Full item detail — used in catalog detail page and admin */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResponse {
        private UUID id;
        private String name;
        private String slug;
        private String description;
        private BigDecimal pricePerDay;
        private Integer quantityInStock;
        private Integer minRentalDays;
        private String dimensions;
        private String color;
        private String material;
        private String careInstructions;
        private Boolean active;
        private Boolean featured;
        private CategorySummary category;
        private List<ImageResponse> images;
        private String primaryImageUrl;
        private Instant createdAt;
    }

    /** Lightweight card for catalog grid */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemCard {
        private UUID id;
        private String name;
        private String slug;
        private BigDecimal pricePerDay;
        private String primaryImageUrl;
        private String categoryName;
        private Boolean featured;
        private Integer quantityInStock;
    }

    /** Image data */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageResponse {
        private UUID id;
        private String s3Url;
        private String altText;
        private Boolean isPrimary;
        private Integer sortOrder;
    }

    /** Category summary embedded in item responses */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private UUID id;
        private String name;
        private String description;
    }

    /** Category with item count — for filter sidebar */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryWithCount {
        private UUID id;
        private String name;
        private long itemCount;
    }

    // ============================================================
    //  REQUEST DTOs (form/API → entity)
    // ============================================================

    /** Create or update an item — used by admin panel */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {

        @NotBlank(message = "Item name is required")
        @Size(max = 200, message = "Name must be under 200 characters")
        private String name;

        @NotNull(message = "Category is required")
        private UUID categoryId;

        @Size(max = 200, message = "Slug must be under 200 characters")
        private String slug;

        private String description;

        @NotNull(message = "Price per day is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Invalid price format")
        private BigDecimal pricePerDay;

        @DecimalMin(value = "0.00", message = "Deposit cannot be negative")
        private BigDecimal depositAmount;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantityInStock;

        private Integer minRentalDays = 1;

        private String dimensions;
        private String color;
        private String material;
        private String careInstructions;

        private Boolean active = true;
        private Boolean featured = false;
        private Integer sortOrder = 0;
    }

    /** Catalog filter/search parameters */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CatalogFilter {
        private UUID categoryId;
        private String search;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 12;
        @Builder.Default
        private String sort = "sortOrder";
    }
}
