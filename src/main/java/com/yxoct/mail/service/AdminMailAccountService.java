package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.AdminMailAccountDriftEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountDriftPage;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningEntry;
import com.yxoct.mail.domain.mail.AdminMailAccountProvisioningPage;
import com.yxoct.mail.persistence.AdminMailAccountDriftRecord;
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

  public AdminMailAccountService(
      AdminMailAccountRepository repository,
      MailAccountReconciliationRepository reconciliationRepository,
      Clock clock) {
    this.repository = repository;
    this.reconciliationRepository = reconciliationRepository;
    this.clock = clock;
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
