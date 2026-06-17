package com.laulain.rentals.config;

import org.springframework.context.annotation.Configuration;

/**
 * AWS configuration — disabled for initial deployment.
 * Re-enable S3, SES, SNS, Cognito beans when AWS integration is added.
 */
@Configuration
public class AwsConfig {
    // AWS SDK clients removed for initial deployment.
    // Add S3Client, SesClient, SnsClient, CognitoIdentityProviderClient beans here
    // when AWS integration is re-enabled.
}
