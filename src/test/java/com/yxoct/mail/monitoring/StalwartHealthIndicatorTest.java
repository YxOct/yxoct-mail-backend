package com.yxoct.mail.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class StalwartHealthIndicatorTest {

  @Mock private StalwartManagementClient managementClient;

  @InjectMocks private StalwartHealthIndicator healthIndicator;

  @Test
  void reportsUpWhenManagementApiIsAvailable() {
    assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void reportsDownWithoutSensitiveDetailsWhenManagementApiFails() {
    doThrow(new StalwartProvisioningException("MANAGEMENT_AUTHENTICATION_FAILED"))
        .when(managementClient)
        .checkAvailability();

    var health = healthIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails())
        .hasSize(1)
        .containsEntry("failureCode", "MANAGEMENT_AUTHENTICATION_FAILED");
  }
}
