package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StalwartPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsCompleteConfiguration() {
    StalwartProperties properties =
        new StalwartProperties(URI.create("https://mail.example.com"), Duration.ofMinutes(1));

    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  void rejectsMissingRequiredConfiguration() {
    StalwartProperties properties = new StalwartProperties(null, null);

    Set<String> invalidProperties =
        validator.validate(properties).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

    assertThat(invalidProperties).containsExactlyInAnyOrder("baseUrl", "sessionCacheTtl");
  }

  @Test
  void rejectsRelativeBaseUrl() {
    StalwartProperties properties =
        new StalwartProperties(URI.create("mail.example.com"), Duration.ofMinutes(1));

    assertThat(validator.validate(properties))
        .singleElement()
        .satisfies(
            violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("baseUrlValid"));
  }

  @Test
  void rejectsNonPositiveSessionCacheTtl() {
    StalwartProperties properties =
        new StalwartProperties(URI.create("https://mail.example.com"), Duration.ZERO);

    assertThat(validator.validate(properties))
        .singleElement()
        .satisfies(
            violation ->
                assertThat(violation.getPropertyPath().toString())
                    .isEqualTo("sessionCacheTtlValid"));
  }
}
