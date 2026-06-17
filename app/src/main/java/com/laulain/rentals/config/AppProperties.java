package com.laulain.rentals.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * Strongly-typed configuration properties bound from application.yml
 * under the "app" prefix.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String name,
        BusinessProperties business
) {
    public record BusinessProperties(
            int bufferDays,
            int quoteExpiryDays,
            int reminderHoursBefore,
            int minBookingDaysAhead,
            double deliveryFeePerMile,
            double taxRate,
            List<String> serviceAreas
    ) {}
}
