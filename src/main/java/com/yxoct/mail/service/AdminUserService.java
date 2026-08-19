package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.AdminUserPage;
import com.yxoct.mail.domain.user.AdminUserSummary;
import com.yxoct.mail.persistence.AdminUserRecord;
import com.yxoct.mail.persistence.AdminUserRepository;
import com.yxoct.mail.persistence.UserStatusMailAccount;
import com.yxoct.mail.persistence.UserStatusManagementRepository;
import com.yxoct.mail.persistence.UserStatusTarget;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminUserService {

  private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

  private final AdminUserRepository repository;
  private final UserStatusManagementRepository statusRepository;
  private final StalwartManagementClient managementClient;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public AdminUserService(
      AdminUserRepository repository,
      UserStatusManagementRepository statusRepository,
      StalwartManagementClient managementClient,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.repository = repository;
    this.statusRepository = statusRepository;
    this.managementClient = managementClient;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  public AdminUserPage list(int page, int size) {
    return new AdminUserPage(
        page,
        size,
        repository.count(),
        repository.findPage(page, size).stream().map(this::map).toList());
  }

  public AdminUserSummary get(long userId) {
    return repository
        .findById(userId)
        .map(this::map)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  public void disable(long operatedByUserId, long userId, String reason) {
    if (operatedByUserId == userId) {
      throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
    }
    String normalizedReason = reason.strip();
    List<String> disabledRemoteAccounts = new ArrayList<>();
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            UserStatusTarget target =
                statusRepository
                    .findUserForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (target.status() == UserStatus.DISABLED) {
              return;
            }
            if (target.role() == UserRole.ADMIN
                && statusRepository.findActiveAdministratorIdsForUpdate().size() <= 1) {
              throw new BusinessException(ErrorCode.CANNOT_DISABLE_LAST_ADMIN);
            }
            List<UserStatusMailAccount> accounts =
                statusRepository.findOwnedMailAccountsForUpdate(userId);
            for (UserStatusMailAccount account : accounts) {
              if (account.status() == MailAccountStatus.ACTIVE
                  && account.stalwartAccountId() != null
                  && !account.stalwartAccountId().isBlank()) {
                disableRemoteAccount(account, disabledRemoteAccounts);
              }
            }

            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            if (!statusRepository.disableUser(userId, operatedByUserId, normalizedReason, now)) {
              throw new IllegalStateException("Locked active user could not be disabled");
            }
            statusRepository.disableOwnedMailAccounts(userId, now);
            statusRepository.revokeRefreshTokens(userId, now);
            statusRepository.saveAudit(audit(userId, operatedByUserId, normalizedReason, now));
          });
    } catch (RuntimeException exception) {
      compensateRemoteAccounts(disabledRemoteAccounts);
      throw exception;
    }
  }

  private void disableRemoteAccount(
      UserStatusMailAccount account, List<String> disabledRemoteAccounts) {
    try {
      managementClient.setAccountEnabled(account.stalwartAccountId(), false);
      disabledRemoteAccounts.add(account.stalwartAccountId());
    } catch (StalwartProvisioningException exception) {
      log.warn(
          "Stalwart account disable failed mailAccountId={} failureCode={} diagnostic={}",
          account.mailAccountId(),
          exception.failureCode(),
          exception.diagnostic());
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
    }
  }

  private void compensateRemoteAccounts(List<String> disabledRemoteAccounts) {
    for (int index = disabledRemoteAccounts.size() - 1; index >= 0; index--) {
      String accountId = disabledRemoteAccounts.get(index);
      try {
        managementClient.setAccountEnabled(accountId, true);
      } catch (StalwartProvisioningException exception) {
        log.error(
            "Could not compensate Stalwart account disable accountId={} failureCode={} diagnostic={}",
            accountId,
            exception.failureCode(),
            exception.diagnostic());
      }
    }
  }

  private UserStatusAuditEntity audit(
      long userId, long operatedByUserId, String reason, LocalDateTime createdAt) {
    UserStatusAuditEntity audit = new UserStatusAuditEntity();
    audit.setUserId(userId);
    audit.setAction(UserStatusAuditAction.DISABLED);
    audit.setReason(reason);
    audit.setOperatedByUserId(operatedByUserId);
    audit.setCreatedAt(createdAt);
    return audit;
  }

  private AdminUserSummary map(AdminUserRecord user) {
    return new AdminUserSummary(
        user.userId(),
        user.primaryEmailAddress(),
        user.displayName(),
        user.role(),
        user.userStatus(),
        user.mailAccountId(),
        user.mailAccountStatus(),
        user.createdAt());
  }
}
