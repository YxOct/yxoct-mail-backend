package com.yxoct.mail.monitoring;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class StalwartHealthIndicator implements HealthIndicator {

  private final StalwartManagementClient managementClient;

  public StalwartHealthIndicator(StalwartManagementClient managementClient) {
    this.managementClient = managementClient;
  }

  @Override
  public Health health() {
    try {
      managementClient.checkAvailability();
      return Health.up().build();
    } catch (StalwartProvisioningException exception) {
      return Health.down().withDetail("failureCode", exception.failureCode()).build();
    }
  }
}
