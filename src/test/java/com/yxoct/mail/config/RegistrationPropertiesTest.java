package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistrationPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsCompleteConfiguration() {
    RegistrationProperties properties =
        new RegistrationProperties("yxoct.com", Duration.ofDays(7), Set.of("admin"));

    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  void rejectsInvalidMailDomain() {
    RegistrationProperties properties =
        new RegistrationProperties("HTTP://YXOct.com", Duration.ofDays(7), Set.of("admin"));

    assertThat(validator.validate(properties))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("mailDomainValid"));
  }

  @Test
  void rejectsNonPositiveInvitationTtl() {
    RegistrationProperties properties =
        new RegistrationProperties("yxoct.com", Duration.ZERO, Set.of("admin"));

    assertThat(validator.validate(properties))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("invitationTtlValid"));
  }
}
