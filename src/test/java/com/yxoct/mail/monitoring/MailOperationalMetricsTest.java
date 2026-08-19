package com.yxoct.mail.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yxoct.mail.persistence.AdminMailAccountRepository;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailOperationalMetricsTest {

  @Mock private AdminMailAccountRepository adminMailAccountRepository;
  @Mock private MailAccountReconciliationRepository reconciliationRepository;

  @Test
  void exposesBacklogAndTaskStateWithoutHighCardinalityTags() {
    var registry = new SimpleMeterRegistry();
    when(adminMailAccountRepository.countProvisioningIssues()).thenReturn(3L);
    when(reconciliationRepository.countDrifts()).thenReturn(2L);
    var metrics =
        new MailOperationalMetrics(registry, adminMailAccountRepository, reconciliationRepository);

    metrics.recordProvisioning("failed");
    metrics.recordLoginRateLimited("ip");
    metrics.recordReconciliationInspection("alias_mismatch");
    metrics.recordReconciliationFailed();
    metrics.recordReconciliationCompleted(Instant.ofEpochSecond(1234));

    assertThat(registry.get("yxoct.mail.provisioning.issues").gauge().value()).isEqualTo(3);
    assertThat(registry.get("yxoct.mail.reconciliation.drifts").gauge().value()).isEqualTo(2);
    assertThat(
            registry.get("yxoct.mail.reconciliation.last.completed.epoch.seconds").gauge().value())
        .isEqualTo(1234);
    assertThat(registry.get("yxoct.mail.reconciliation.consecutive.failures").gauge().value())
        .isZero();
    assertThat(
            registry
                .get("yxoct.mail.provisioning.attempts")
                .tag("outcome", "failed")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("yxoct.mail.authentication.login.rate.limited")
                .tag("dimension", "ip")
                .counter()
                .count())
        .isEqualTo(1);
  }
}
