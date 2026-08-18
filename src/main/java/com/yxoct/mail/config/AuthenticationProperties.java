package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.authentication")
public record AuthenticationProperties(
    @NotBlank String issuer, String jwtSecret, @NotNull Duration accessTokenTtl) {

  private static final String BASE64_URL_256_BIT_PATTERN = "[A-Za-z0-9_-]{43}";

  @AssertTrue(message = "jwt-secret must be an unpadded Base64URL-encoded 256-bit key")
  public boolean isJwtSecretValid() {
    try {
      return jwtSecret != null
          && jwtSecret.matches(BASE64_URL_256_BIT_PATTERN)
          && Base64.getUrlDecoder().decode(jwtSecret).length == 32;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @AssertTrue(message = "access-token-ttl must be greater than zero")
  public boolean isAccessTokenTtlValid() {
    return accessTokenTtl == null || (!accessTokenTtl.isZero() && !accessTokenTtl.isNegative());
  }

  public byte[] decodedJwtSecret() {
    return Base64.getUrlDecoder().decode(jwtSecret);
  }

  @Override
  public String toString() {
    return "AuthenticationProperties[issuer="
        + issuer
        + ", jwtSecret=***, accessTokenTtl="
        + accessTokenTtl
        + "]";
  }
}
