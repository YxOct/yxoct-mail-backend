package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ApplicationTimeConfigTest {

  private final ApplicationTimeConfig config = new ApplicationTimeConfig();

  @Test
  void createsClockForConfiguredApplicationTimeZone() {
    assertThat(config.applicationClock("Asia/Shanghai").getZone())
        .isEqualTo(ZoneId.of("Asia/Shanghai"));
  }

  @Test
  void rejectsUnknownApplicationTimeZone() {
    assertThatThrownBy(() -> config.applicationClock("Mars/Olympus"))
        .isInstanceOf(DateTimeException.class);
  }
}
