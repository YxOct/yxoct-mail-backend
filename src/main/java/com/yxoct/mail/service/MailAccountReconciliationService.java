package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartAccountMetadata;
import com.yxoct.mail.client.stalwart.StalwartAccountSnapshot;
import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import com.yxoct.mail.config.StalwartReconciliationProperties;
import com.yxoct.mail.monitoring.MailOperationalMetrics;
import com.yxoct.mail.persistence.MailAccountReconciliationCandidate;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MailAccountReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(MailAccountReconciliationService.class);
  private final MailAccountReconciliationRepository repository;
  private final StalwartManagementClient managementClient;
  private final StalwartProvisioningProperties provisioningProperties;
  private final StalwartReconciliationProperties reconciliationProperties;
  private final ReconciliationLeaseCoordinator leaseCoordinator;
  private final Clock clock;
  private final MailOperationalMetrics metrics;

  public MailAccountReconciliationService(
      MailAccountReconciliationRepository repository,
      StalwartManagementClient managementClient,
      StalwartProvisioningProperties provisioningProperties,
      StalwartReconciliationProperties reconciliationProperties,
      ReconciliationLeaseCoordinator leaseCoordinator,
      MailOperationalMetrics metrics,
      Clock clock) {
    this.repository = repository;
    this.managementClient = managementClient;
    this.provisioningProperties = provisioningProperties;
    this.reconciliationProperties = reconciliationProperties;
    this.leaseCoordinator = leaseCoordinator;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${stalwart.reconciliation.scan-interval}")
  public void reconcileAccounts() {
    if (!provisioningProperties.enabled()) {
      return;
    }
    LocalDateTime startedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    if (!leaseCoordinator.tryAcquire(startedAt)) {
      metrics.recordReconciliationLeaseSkipped();
      return;
    }
    try {
      for (MailAccountReconciliationCandidate candidate :
          repository.findCandidates(reconciliationProperties.batchSize())) {
        reconcile(candidate);
      }
      metrics.recordReconciliationCompleted(clock.instant());
    } catch (RuntimeException exception) {
      metrics.recordReconciliationFailed();
      throw exception;
    }
  }

  void reconcile(MailAccountReconciliationCandidate candidate) {
    LocalDateTime checkedAt = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    try {
      Optional<StalwartAccountSnapshot> remote =
          managementClient.inspectAccount(candidate.stalwartAccountId());
      if (remote.isEmpty()) {
        repository.saveResult(
            candidate.mailAccountId(),
            MailAccountDriftType.REMOTE_ACCOUNT_MISSING,
            null,
            checkedAt);
        metrics.recordReconciliationInspection("remote_account_missing");
        return;
      }
      boolean expectedEnabled = candidate.status() == MailAccountStatus.ACTIVE;
      MailAccountDriftType driftType =
          remote.get().enabled() == expectedEnabled
              ? null
              : MailAccountDriftType.ENABLED_STATE_MISMATCH;
      if (driftType == null) {
        driftType = detectMetadataDrift(candidate);
      }
      repository.saveResult(candidate.mailAccountId(), driftType, null, checkedAt);
      metrics.recordReconciliationInspection(
          driftType == null ? "in_sync" : driftType.name().toLowerCase(java.util.Locale.ROOT));
    } catch (StalwartProvisioningException exception) {
      repository.saveResult(
          candidate.mailAccountId(),
          MailAccountDriftType.INSPECTION_FAILED,
          exception.failureCode(),
          checkedAt);
      metrics.recordReconciliationInspection("inspection_failed");
      log.warn(
          "Stalwart reconciliation failed mailAccountId={} failureCode={}",
          candidate.mailAccountId(),
          exception.failureCode());
    }
  }

  private MailAccountDriftType detectMetadataDrift(MailAccountReconciliationCandidate candidate) {
    String domain =
        candidate.emailAddress().substring(candidate.emailAddress().lastIndexOf('@') + 1);
    StalwartAccountMetadata metadata =
        managementClient.inspectAccountMetadata(candidate.stalwartAccountId(), domain);
    if (!java.util.Objects.equals(candidate.displayName(), metadata.displayName())) {
      return MailAccountDriftType.DISPLAY_NAME_MISMATCH;
    }
    Set<String> expectedAliases =
        Set.copyOf(repository.findExpectedAliases(candidate.mailAccountId()));
    return expectedAliases.equals(metadata.aliases()) ? null : MailAccountDriftType.ALIAS_MISMATCH;
  }
}
