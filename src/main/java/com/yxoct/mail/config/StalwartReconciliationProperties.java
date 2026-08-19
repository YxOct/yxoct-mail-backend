package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stalwart.reconciliation")
public record StalwartReconciliationProperties(
    @NotNull Duration scanInterval,
    @NotNull Duration leaseDuration,
    @Min(1) @Max(1000) int batchSize) {

  @AssertTrue(message = "reconciliation durations must be greater than zero")
  public boolean isDurationsValid() {
    return isPositive(scanInterval) && isPositive(leaseDuration);
  }

  private boolean isPositive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
