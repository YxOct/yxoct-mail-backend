package com.yxoct.mail.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public record LoginRateLimitProperties(@Min(1) int maxFailures, @NotNull Duration window) {}
