package com.yxoct.mail.config;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stalwart")
public record StalwartProperties(@NotBlank URI baseUrl) {}
