package com.yxoct.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ApplicationTimeConfigTest {

  private final ApplicationTimeConfig config = new ApplicationTimeConfig();

  @Test
  void createsClockForConfiguredApplicationTimeZone() {
    assertThat(
            config
                .applicationClock(new ApplicationTimeProperties(ZoneId.of("Asia/Shanghai")))
                .getZone())
        .isEqualTo(ZoneId.of("Asia/Shanghai"));
  }
}
