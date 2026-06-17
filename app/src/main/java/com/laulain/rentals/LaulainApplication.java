package com.laulain.rentals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Laulain Luxe Rentals — Main Application Entry Point
 *
 * Event rental management platform for Laulain Luxe Rentals, Fate TX.
 * Manages chafing dishes, event equipment rental, online reservations,
 * digital quotes, contracts, and payments.
 */
@SpringBootApplication
@EnableCaching          // Redis-backed caching (availability, catalog)
@EnableAsync            // Async email/SMS sending via SES + SNS
@EnableScheduling       // Cron jobs: reminders, quote expiration cleanup
@ConfigurationPropertiesScan("com.laulain.rentals.config")
public class LaulainApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaulainApplication.class, args);
    }
}
