package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StalwartProvisioningPropertiesTest {

  private static final String VALID_KEY =
      Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsDisabledProvisioningWithoutSecrets() {
    assertThat(validator.validate(properties(false, "", ""))).isEmpty();
  }

  @Test
  void acceptsEnabledProvisioningWithValidSecrets() {
    StalwartProvisioningProperties properties = properties(true, "API-secret", VALID_KEY);

    assertThat(validator.validate(properties)).isEmpty();
    assertThat(properties.toString()).doesNotContain("API-secret", VALID_KEY);
  }

  @Test
  void rejectsMissingOrInvalidEnabledSecrets() {
    StalwartProvisioningProperties properties = properties(true, "", "too-short");

    Set<String> invalidProperties =
        validator.validate(properties).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertThat(invalidProperties)
        .containsExactlyInAnyOrder("managementApiKeyValid", "credentialEncryptionKeyValid");
  }

  @Test
  void rejectsPaddedBase64EncryptionKey() {
    String paddedKey = Base64.getEncoder().encodeToString(new byte[32]);

    assertThat(validator.validate(properties(true, "API-secret", paddedKey)))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("credentialEncryptionKeyValid");
  }

  private StalwartProvisioningProperties properties(
      boolean enabled, String apiKey, String encryptionKey) {
    return new StalwartProvisioningProperties(
        enabled,
        apiKey,
        encryptionKey,
        Duration.ofSeconds(10),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        Duration.ofHours(1),
        20);
  }
}
