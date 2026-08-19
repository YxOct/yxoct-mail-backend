package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stalwart.provisioning")
public record StalwartProvisioningProperties(
    boolean enabled,
    String managementApiKey,
    String credentialEncryptionKey,
    @NotNull Duration scanInterval,
    @NotNull Duration leaseDuration,
    @NotNull Duration initialRetryDelay,
    @NotNull Duration maxRetryDelay,
    @Min(1) int batchSize) {

  private static final String BASE64_URL_256_BIT_PATTERN = "[A-Za-z0-9_-]{43}";

  @AssertTrue(message = "management-api-key is required")
  public boolean isManagementApiKeyValid() {
    return managementApiKey != null && !managementApiKey.isBlank();
  }

  @AssertTrue(
      message = "credential-encryption-key must be an unpadded Base64URL-encoded 256-bit key")
  public boolean isCredentialEncryptionKeyValid() {
    if (!enabled) {
      return true;
    }
    try {
      return credentialEncryptionKey != null
          && credentialEncryptionKey.matches(BASE64_URL_256_BIT_PATTERN)
          && Base64.getUrlDecoder().decode(credentialEncryptionKey).length == 32;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  @AssertTrue(message = "provisioning durations must be greater than zero")
  public boolean areDurationsValid() {
    return isPositive(scanInterval)
        && isPositive(leaseDuration)
        && isPositive(initialRetryDelay)
        && isPositive(maxRetryDelay)
        && (initialRetryDelay == null
            || maxRetryDelay == null
            || initialRetryDelay.compareTo(maxRetryDelay) <= 0);
  }

  public byte[] decodedCredentialEncryptionKey() {
    return Base64.getUrlDecoder().decode(credentialEncryptionKey);
  }

  @Override
  public String toString() {
    return "StalwartProvisioningProperties[enabled="
        + enabled
        + ", managementApiKey=***, credentialEncryptionKey=***, scanInterval="
        + scanInterval
        + ", leaseDuration="
        + leaseDuration
        + ", initialRetryDelay="
        + initialRetryDelay
        + ", maxRetryDelay="
        + maxRetryDelay
        + ", batchSize="
        + batchSize
        + "]";
  }

  private boolean isPositive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
