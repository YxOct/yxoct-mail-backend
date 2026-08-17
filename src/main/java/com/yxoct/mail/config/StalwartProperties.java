package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stalwart")
public record StalwartProperties(
    @NotNull URI baseUrl, @NotBlank String username, @NotBlank String password) {

  @AssertTrue(message = "must be an absolute HTTP(S) URL")
  public boolean isBaseUrlValid() {
    return baseUrl == null
        || (baseUrl.isAbsolute()
            && baseUrl.getHost() != null
            && ("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme())));
  }
}
