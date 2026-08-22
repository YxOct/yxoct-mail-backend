package com.yxoct.mail.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mail.restore-cleanup")
public record EmailRestoreCleanupProperties(
    boolean enabled,
    @NotNull Duration scanInterval,
    @NotNull Duration retention,
    @NotNull Duration leaseDuration,
    @Min(1) @Max(1000) int batchSize) {

  @AssertTrue(message = "restore cleanup durations must be greater than zero")
  public boolean isDurationsValid() {
    return isPositive(scanInterval) && isPositive(retention) && isPositive(leaseDuration);
  }

  private boolean isPositive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
