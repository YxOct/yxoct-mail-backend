package com.yxoct.mail.monitoring;

import com.yxoct.mail.persistence.AdminMailAccountRepository;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MailOperationalMetrics {

  private final MeterRegistry registry;
  private final AtomicLong reconciliationLastCompleted = new AtomicLong();
  private final AtomicLong reconciliationConsecutiveFailures = new AtomicLong();

  public MailOperationalMetrics(
      MeterRegistry registry,
      AdminMailAccountRepository adminMailAccountRepository,
      MailAccountReconciliationRepository reconciliationRepository) {
    this.registry = registry;
    Gauge.builder(
            "yxoct.mail.provisioning.issues",
            adminMailAccountRepository,
            AdminMailAccountRepository::countProvisioningIssues)
        .description("Mail accounts waiting for or failing Stalwart provisioning")
        .register(registry);
    Gauge.builder(
            "yxoct.mail.reconciliation.drifts",
            reconciliationRepository,
            MailAccountReconciliationRepository::countDrifts)
        .description("Mail accounts with detected Stalwart drift")
        .register(registry);
    Gauge.builder(
            "yxoct.mail.reconciliation.last.completed.epoch.seconds",
            reconciliationLastCompleted,
            AtomicLong::get)
        .description("Epoch seconds when Stalwart reconciliation last completed")
        .register(registry);
    Gauge.builder(
            "yxoct.mail.reconciliation.consecutive.failures",
            reconciliationConsecutiveFailures,
            AtomicLong::get)
        .description("Consecutive failed Stalwart reconciliation runs")
        .register(registry);
  }

  public void recordProvisioning(String outcome) {
    counter("yxoct.mail.provisioning.attempts", "outcome", outcome).increment();
  }

  public void recordLoginRateLimited(String dimension) {
    counter("yxoct.mail.authentication.login.rate.limited", "dimension", dimension).increment();
  }

  public void recordReconciliationInspection(String outcome) {
    counter("yxoct.mail.reconciliation.inspections", "outcome", outcome).increment();
  }

  public void recordReconciliationLeaseSkipped() {
    counter("yxoct.mail.reconciliation.runs", "outcome", "lease_skipped").increment();
  }

  public void recordReconciliationCompleted(Instant completedAt) {
    reconciliationLastCompleted.set(completedAt.getEpochSecond());
    reconciliationConsecutiveFailures.set(0);
    counter("yxoct.mail.reconciliation.runs", "outcome", "completed").increment();
  }

  public void recordReconciliationFailed() {
    reconciliationConsecutiveFailures.incrementAndGet();
    counter("yxoct.mail.reconciliation.runs", "outcome", "failed").increment();
  }

  private Counter counter(String name, String tagName, String tagValue) {
    return Counter.builder(name).tag(tagName, tagValue).register(registry);
  }
}
