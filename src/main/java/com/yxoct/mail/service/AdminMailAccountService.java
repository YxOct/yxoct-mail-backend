package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.AdminMailAccountDriftEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountDriftPage;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningPage;
import com.yxoct.mail.persistence.AdminMailAccountDriftRecord;
import com.yxoct.mail.persistence.AdminMailAccountDriftTarget;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningRecord;
import com.yxoct.mail.persistence.AdminMailAccountProvisioningTarget;
import com.yxoct.mail.persistence.AdminMailAccountRepository;
import com.yxoct.mail.persistence.MailAccountReconciliationRepository;
import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMailAccountService {

  private final AdminMailAccountRepository repository;
  private final MailAccountReconciliationRepository reconciliationRepository;
  private final Clock clock;
  private final StalwartManagementClient managementClient;

  public AdminMailAccountService(
      AdminMailAccountRepository repository,
      MailAccountReconciliationRepository reconciliationRepository,
      StalwartManagementClient managementClient,
      Clock clock) {
    this.repository = repository;
    this.reconciliationRepository = reconciliationRepository;
    this.managementClient = managementClient;
    this.clock = clock;
  }

  @Transactional
  public void repairDrift(long operatedByUserId, long mailAccountId) {
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    AdminMailAccountDriftTarget target =
        repository
            .findDriftForUpdate(mailAccountId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    switch (target.driftType()) {
      case REMOTE_ACCOUNT_MISSING -> repairMissingAccount(target, now);
      case ENABLED_STATE_MISMATCH -> repairEnabledState(target);
      case DISPLAY_NAME_MISMATCH -> repairDisplayName(target);
      case ALIAS_MISMATCH -> repairAliases(target);
      case INSPECTION_FAILED ->
          throw new BusinessException(ErrorCode.MAIL_ACCOUNT_DRIFT_REPAIR_CONFLICT);
    }
    reconciliationRepository.clearResult(mailAccountId);
    repository.saveDriftRepairAudit(
        target.userId(), operatedByUserId, mailAccountId, target.driftType().name(), now);
  }

  private void repairDisplayName(AdminMailAccountDriftTarget target) {
    requireRemoteAccount(target);
    try {
      managementClient.updateAccountDisplayName(target.stalwartAccountId(), target.displayName());
    } catch (StalwartProvisioningException exception) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
    }
  }

  private void repairAliases(AdminMailAccountDriftTarget target) {
    requireRemoteAccount(target);
    String domain = target.emailAddress().substring(target.emailAddress().lastIndexOf('@') + 1);
    try {
      var remote = managementClient.inspectAccountMetadata(target.stalwartAccountId(), domain);
      var expected =
          java.util.Set.copyOf(
              reconciliationRepository.findExpectedAliases(target.mailAccountId()));
      for (String alias : expected) {
        if (!remote.aliases().contains(alias)) {
          managementClient.addAccountAlias(target.stalwartAccountId(), alias);
        }
      }
      for (String alias : remote.aliases()) {
        if (!expected.contains(alias)) {
          managementClient.removeAccountAlias(target.stalwartAccountId(), alias);
        }
      }
    } catch (StalwartProvisioningException exception) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
    }
  }

  private void requireRemoteAccount(AdminMailAccountDriftTarget target) {
    if (target.stalwartAccountId() == null || target.stalwartAccountId().isBlank()) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_DRIFT_REPAIR_CONFLICT);
    }
  }

  private void repairMissingAccount(AdminMailAccountDriftTarget target, LocalDateTime now) {
    if (target.localStatus() != MailAccountStatus.ACTIVE
        || !repository.scheduleMissingAccountReprovisioning(target.mailAccountId(), now)) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_DRIFT_REPAIR_CONFLICT);
    }
  }

  private void repairEnabledState(AdminMailAccountDriftTarget target) {
    if (target.stalwartAccountId() == null || target.stalwartAccountId().isBlank()) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_DRIFT_REPAIR_CONFLICT);
    }
    boolean expectedEnabled = target.localStatus() == MailAccountStatus.ACTIVE;
    if (!expectedEnabled && target.localStatus() != MailAccountStatus.DISABLED) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_DRIFT_REPAIR_CONFLICT);
    }
    try {
      managementClient.setAccountEnabled(target.stalwartAccountId(), expectedEnabled);
    } catch (StalwartProvisioningException exception) {
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
    }
  }

  public AdminMailAccountDriftPage listDrifts(int page, int size) {
    return new AdminMailAccountDriftPage(
        page,
        size,
        reconciliationRepository.countDrifts(),
        reconciliationRepository.findDrifts(page, size).stream().map(this::mapDrift).toList());
  }

  public AdminMailAccountProvisioningPage listProvisioningIssues(int page, int size) {
    return new AdminMailAccountProvisioningPage(
        page,
        size,
        repository.countProvisioningIssues(),
        repository.findProvisioningIssues(page, size).stream().map(this::map).toList());
  }

  @Transactional
  public void retryProvisioning(long operatedByUserId, long mailAccountId) {
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    AdminMailAccountProvisioningTarget target =
        repository
            .findForRetryForUpdate(mailAccountId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!canRetry(target, now)) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_RETRY_CONFLICT);
    }
    if (!repository.scheduleRetry(mailAccountId, now)) {
      throw new IllegalStateException("Locked mail account could not be scheduled for retry");
    }
    repository.saveRetryAudit(target.userId(), operatedByUserId, mailAccountId, now);
  }

  private boolean canRetry(AdminMailAccountProvisioningTarget target, LocalDateTime now) {
    if (target.status() == MailAccountStatus.FAILED) {
      return true;
    }
    return target.status() == MailAccountStatus.PROVISIONING
        && (target.provisioningLeaseUntil() == null
            || !target.provisioningLeaseUntil().isAfter(now));
  }

  private AdminMailAccountProvisioningEntry map(AdminMailAccountProvisioningRecord account) {
    return new AdminMailAccountProvisioningEntry(
        account.mailAccountId(),
        account.userId(),
        account.emailAddress(),
        account.status(),
        account.provisioningAttempts(),
        account.lastProvisioningError(),
        account.nextProvisioningAt(),
        account.provisioningLeaseUntil(),
        account.updatedAt());
  }

  private AdminMailAccountDriftEntry mapDrift(AdminMailAccountDriftRecord drift) {
    return new AdminMailAccountDriftEntry(
        drift.mailAccountId(),
        drift.userId(),
        drift.emailAddress(),
        drift.localStatus(),
        drift.stalwartAccountId(),
        MailAccountDriftType.valueOf(drift.driftType()),
        drift.lastError(),
        drift.checkedAt());
  }
}
