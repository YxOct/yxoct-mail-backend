package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartAccountSnapshot;
import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import com.yxoct.mail.persistence.MailAccountReconciliationCandidate;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MailAccountReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(MailAccountReconciliationService.class);
  private static final int BATCH_SIZE = 20;

  private final MailAccountReconciliationRepository repository;
  private final StalwartManagementClient managementClient;
  private final StalwartProvisioningProperties properties;
  private final Clock clock;

  public MailAccountReconciliationService(
      MailAccountReconciliationRepository repository,
      StalwartManagementClient managementClient,
      StalwartProvisioningProperties properties,
      Clock clock) {
    this.repository = repository;
    this.managementClient = managementClient;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${stalwart.reconciliation.scan-interval:5m}")
  public void reconcileAccounts() {
    if (!properties.enabled()) {
      return;
    }
    for (MailAccountReconciliationCandidate candidate : repository.findCandidates(BATCH_SIZE)) {
      reconcile(candidate);
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
        return;
      }
      boolean expectedEnabled = candidate.status() == MailAccountStatus.ACTIVE;
      MailAccountDriftType driftType =
          remote.get().enabled() == expectedEnabled
              ? null
              : MailAccountDriftType.ENABLED_STATE_MISMATCH;
      repository.saveResult(candidate.mailAccountId(), driftType, null, checkedAt);
    } catch (StalwartProvisioningException exception) {
      repository.saveResult(
          candidate.mailAccountId(),
          MailAccountDriftType.INSPECTION_FAILED,
          exception.failureCode(),
          checkedAt);
      log.warn(
          "Stalwart reconciliation failed mailAccountId={} failureCode={}",
          candidate.mailAccountId(),
          exception.failureCode());
    }
  }
}
