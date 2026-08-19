package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StalwartReconciliationPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsPositiveDurationsAndBoundedBatchSize() {
    var properties =
        new StalwartReconciliationProperties(Duration.ofMinutes(5), Duration.ofMinutes(10), 20);

    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  void rejectsInvalidDurationsAndBatchSize() {
    var properties =
        new StalwartReconciliationProperties(Duration.ZERO, Duration.ofMinutes(-1), 1001);

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactlyInAnyOrder("durationsValid", "batchSize");
  }
}
