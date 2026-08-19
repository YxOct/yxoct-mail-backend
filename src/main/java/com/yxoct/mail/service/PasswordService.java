package com.yxoct.mail.service;

import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.domain.user.TemporaryPasswordResponse;
import com.yxoct.mail.persistence.PasswordChangeTarget;
import com.yxoct.mail.persistence.UserPasswordRepository;
import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import com.yxoct.mail.security.UserAuthVersionStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordService {
  private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

  private final UserPasswordRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final UserAuthVersionStore authVersionStore;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public PasswordService(
      UserPasswordRepository repository,
      PasswordEncoder passwordEncoder,
      UserAuthVersionStore authVersionStore,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.authVersionStore = authVersionStore;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  public void change(long userId, String currentPassword, String newPassword) {
    long[] previousAuthVersion = new long[1];
    boolean[] authVersionChanged = new boolean[1];
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            PasswordChangeTarget target =
                repository
                    .findForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!passwordEncoder.matches(currentPassword, target.passwordHash())) {
              throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
            }
            if (passwordEncoder.matches(newPassword, target.passwordHash())) {
              throw new BusinessException(ErrorCode.NEW_PASSWORD_MUST_DIFFER);
            }
            String passwordHash = passwordEncoder.encode(newPassword);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            previousAuthVersion[0] = target.version();
            authVersionStore.setVersion(userId, target.version() + 1);
            authVersionChanged[0] = true;
            if (!repository.updatePassword(userId, target.version(), passwordHash, false, now)) {
              throw new IllegalStateException("Locked user password could not be changed");
            }
            repository.revokeRefreshTokens(userId, now);
            UserStatusAuditEntity audit = new UserStatusAuditEntity();
            audit.setUserId(userId);
            audit.setAction(UserStatusAuditAction.PASSWORD_CHANGED);
            audit.setOperatedByUserId(userId);
            audit.setCreatedAt(now);
            repository.saveAudit(audit);
          });
    } catch (RuntimeException exception) {
      restoreAuthVersion(userId, previousAuthVersion[0], authVersionChanged[0]);
      throw exception;
    }
  }

  public TemporaryPasswordResponse resetByAdministrator(long operatedByUserId, long userId) {
    if (operatedByUserId == userId) {
      throw new BusinessException(ErrorCode.CANNOT_RESET_OWN_PASSWORD);
    }
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    resetByAdministrator(operatedByUserId, userId, temporaryPassword);
    return new TemporaryPasswordResponse(temporaryPassword, true);
  }

  private void resetByAdministrator(long operatedByUserId, long userId, String temporaryPassword) {
    long[] previousAuthVersion = new long[1];
    boolean[] authVersionChanged = new boolean[1];
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            PasswordChangeTarget target =
                repository
                    .findForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            String passwordHash = passwordEncoder.encode(temporaryPassword);
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            previousAuthVersion[0] = target.version();
            authVersionStore.setVersion(userId, target.version() + 1);
            authVersionChanged[0] = true;
            if (!repository.updatePassword(userId, target.version(), passwordHash, true, now)) {
              throw new IllegalStateException("Locked user password could not be reset");
            }
            repository.revokeRefreshTokens(userId, now);
            UserStatusAuditEntity audit = new UserStatusAuditEntity();
            audit.setUserId(userId);
            audit.setAction(UserStatusAuditAction.PASSWORD_RESET_BY_ADMIN);
            audit.setOperatedByUserId(operatedByUserId);
            audit.setCreatedAt(now);
            repository.saveAudit(audit);
          });
    } catch (RuntimeException exception) {
      restoreAuthVersion(userId, previousAuthVersion[0], authVersionChanged[0]);
      throw exception;
    }
  }

  private void restoreAuthVersion(long userId, long version, boolean changed) {
    if (!changed) {
      return;
    }
    try {
      authVersionStore.setVersion(userId, version);
    } catch (RuntimeException exception) {
      log.error("Could not restore user authentication version userId={}", userId, exception);
    }
  }
}
