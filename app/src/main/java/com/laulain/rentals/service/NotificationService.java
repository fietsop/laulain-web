package com.laulain.rentals.service;

import com.laulain.rentals.model.Booking;
import com.laulain.rentals.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * NotificationService stub — AWS SES/SNS disabled for initial deployment.
 * All notifications are logged to console only.
 * Re-enable full implementation when SES/SNS is configured.
 */
@Service
@Slf4j
public class NotificationService {

    @Async
    public void sendBookingConfirmation(Booking booking) {
        log.info("[NOTIFY STUB] Booking confirmation for {} — customer: {}",
                booking.getBookingNumber(),
                booking.getCustomer().getEmail());
    }

    @Async
    public void sendAdminNewBookingAlert(Booking booking) {
        log.info("[NOTIFY STUB] Admin alert — new booking: {} from {}",
                booking.getBookingNumber(),
                booking.getCustomer().getEmail());
    }

    @Async
    public void sendBookingConfirmedNotification(Booking booking) {
        log.info("[NOTIFY STUB] Booking confirmed notification for {}",
                booking.getBookingNumber());
    }

    @Async
    public void sendEventReminder(Booking booking) {
        log.info("[NOTIFY STUB] Event reminder for {} on {}",
                booking.getBookingNumber(),
                booking.getEventDate());
    }

    @Async
    public void sendCancellationNotification(Booking booking) {
        log.info("[NOTIFY STUB] Cancellation notification for {}",
                booking.getBookingNumber());
    }

    @Async
    public void sendPaymentReceipt(Payment payment) {
        log.info("[NOTIFY STUB] Payment receipt for payment {} on booking {}",
                payment.getId(),
                payment.getBooking().getBookingNumber());
    }
}
