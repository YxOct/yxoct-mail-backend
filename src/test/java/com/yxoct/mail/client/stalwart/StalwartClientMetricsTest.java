package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StalwartClientMetricsTest {

  private SimpleMeterRegistry meterRegistry;
  private StalwartClientMetrics metrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new StalwartClientMetrics(meterRegistry);
  }

  @Test
  void recordsSuccessfulRequest() {
    assertThat(metrics.record("email.query", () -> "result")).isEqualTo("result");

    assertThat(timerCount("email.query", "success")).isEqualTo(1);
  }

  @Test
  void recordsClassifiedBusinessFailure() {
    assertThatThrownBy(
            () ->
                metrics.record(
                    "session",
                    () -> {
                      throw new BusinessException(ErrorCode.MAIL_SERVICE_TIMEOUT);
                    }))
        .isInstanceOf(BusinessException.class);

    assertThat(timerCount("session", "timeout")).isEqualTo(1);
  }

  @Test
  void recordsUnexpectedFailure() {
    assertThatThrownBy(
            () ->
                metrics.record(
                    "mailbox.list",
                    () -> {
                      throw new IllegalStateException("failure");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(timerCount("mailbox.list", "unexpected")).isEqualTo(1);
  }

  private long timerCount(String operation, String outcome) {
    return meterRegistry
        .get(StalwartClientMetrics.METRIC_NAME)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .timer()
        .count();
  }
}
