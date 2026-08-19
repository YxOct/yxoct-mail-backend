package com.yxoct.mail.service;

import com.yxoct.mail.client.stalwart.StalwartManagementClient;
import com.yxoct.mail.client.stalwart.StalwartProvisioningException;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.mail.MailAccountSettings;
import com.yxoct.mail.persistence.MailAccountSettingsRepository;
import com.yxoct.mail.persistence.OwnedMailAccount;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailAccountSettingsService {

  private static final Logger log = LoggerFactory.getLogger(MailAccountSettingsService.class);

  private final MailAccountSettingsRepository repository;
  private final StalwartManagementClient managementClient;
  private final DisplayNameNormalizer displayNameNormalizer;
  private final Clock clock;

  public MailAccountSettingsService(
      MailAccountSettingsRepository repository,
      StalwartManagementClient managementClient,
      DisplayNameNormalizer displayNameNormalizer,
      Clock clock) {
    this.repository = repository;
    this.managementClient = managementClient;
    this.displayNameNormalizer = displayNameNormalizer;
    this.clock = clock;
  }

  @Transactional
  public MailAccountSettings updateDisplayName(
      String authenticatedUserId, long mailAccountId, String requestedDisplayName) {
    long userId = parseUserId(authenticatedUserId);
    String displayName = displayNameNormalizer.normalizeRequired(requestedDisplayName);
    OwnedMailAccount account =
        repository
            .findOwnedForUpdate(userId, mailAccountId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    if (account.status() != MailAccountStatus.ACTIVE
        || account.stalwartAccountId() == null
        || account.stalwartAccountId().isBlank()) {
      throw new BusinessException(ErrorCode.MAIL_ACCOUNT_NOT_READY);
    }
    if (displayName.equals(account.displayName())) {
      return new MailAccountSettings(mailAccountId, displayName);
    }

    try {
      managementClient.updateAccountDisplayName(account.stalwartAccountId(), displayName);
    } catch (StalwartProvisioningException exception) {
      log.warn(
          "Stalwart display name update failed mailAccountId={} failureCode={} diagnostic={}",
          mailAccountId,
          exception.failureCode(),
          exception.diagnostic());
      throw new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, exception);
    }
    boolean updated =
        repository.updateDisplayName(
            mailAccountId, displayName, LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
    if (!updated) {
      throw new IllegalStateException("Mail account disappeared while updating display name");
    }
    return new MailAccountSettings(mailAccountId, displayName);
  }

  private long parseUserId(String subject) {
    try {
      return Long.parseLong(subject);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED, exception);
    }
  }
}
