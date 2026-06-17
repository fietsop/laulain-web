package com.laulain.rentals.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects common model attributes into every Thymeleaf view.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final AppProperties appProperties;

    @ModelAttribute("contactEmail")
    public String contactEmail() {
        return "reservations@laulainluxerentals.com";
    }

    @ModelAttribute("appName")
    public String appName() {
        return appProperties.name();
    }

    @ModelAttribute("appBaseUrl")
    public String appBaseUrl() {
        return appProperties.baseUrl();
    }
}
