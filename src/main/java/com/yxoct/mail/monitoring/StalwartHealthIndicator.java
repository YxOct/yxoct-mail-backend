package com.yxoct.mail.monitoring;

import com.yxoct.mail.client.stalwart.JmapClient;
import com.yxoct.mail.common.exception.BusinessException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class StalwartHealthIndicator implements HealthIndicator {

  private final JmapClient jmapClient;

  public StalwartHealthIndicator(JmapClient jmapClient) {
    this.jmapClient = jmapClient;
  }

  @Override
  public Health health() {
    try {
      jmapClient.getSession();
      return Health.up().build();
    } catch (BusinessException exception) {
      return Health.down().withDetail("errorCode", exception.getErrorCode().getCode()).build();
    }
  }
}
