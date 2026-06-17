package com.laulain.rentals.service;

import com.laulain.rentals.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled jobs for Laulain Luxe Rentals.
 *
 * Runs inside the EKS pod — only one replica should handle scheduling.
 * For multi-pod deployments, use ShedLock or Spring's @Scheduled with Redis lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final BookingService bookingService;
    private final AppProperties appProperties;

    /**
     * Send event reminders every morning at 8:00 AM Central Time.
     * Reminds customers whose event is in exactly N hours (configured: 48hrs).
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Chicago")
    public void sendEventReminders() {
        int reminderHours = appProperties.business().reminderHoursBefore();
        long daysAhead = reminderHours / 24;
        LocalDate targetDate = LocalDate.now().plusDays(daysAhead);

        log.info("Running event reminder job for date: {}", targetDate);
        bookingService.sendRemindersForDate(targetDate);
    }

    /**
     * Log daily availability summary at 7:00 AM.
     * Useful for monitoring — can be extended to email a daily digest to admin.
     */
    @Scheduled(cron = "0 0 7 * * *", zone = "America/Chicago")
    public void dailySummary() {
        log.info("=== Laulain Luxe Rentals — Daily Summary [{}] ===", LocalDate.now());
    }
}
