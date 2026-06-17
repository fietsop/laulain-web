package com.laulain.rentals.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;

/**
 * Registers the Thymeleaf Java 8 Time dialect.
 *
 * This enables the #temporals utility object in templates:
 *   ${#temporals.format(someInstant, 'MMM d, yyyy h:mm a')}
 *   ${#temporals.format(someLocalDate, 'MMMM d, yyyy')}
 *
 * Spring Boot auto-configures this when thymeleaf-extras-java8time is on the
 * classpath, but registering it explicitly guarantees it even in custom configs.
 */
@Configuration
public class ThymeleafConfig {

    @Bean
    public Java8TimeDialect java8TimeDialect() {
        return new Java8TimeDialect();
    }
}
