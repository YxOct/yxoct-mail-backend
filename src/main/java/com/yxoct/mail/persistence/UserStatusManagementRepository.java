package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import com.yxoct.mail.persistence.mapper.RefreshTokenSessionMapper;
import com.yxoct.mail.persistence.mapper.UserStatusAuditMapper;
import com.yxoct.mail.persistence.mapper.UserStatusManagementMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserStatusManagementRepository {

  private final UserStatusManagementMapper statusMapper;
  private final UserStatusAuditMapper auditMapper;
  private final RefreshTokenSessionMapper refreshTokenMapper;

  public UserStatusManagementRepository(
      UserStatusManagementMapper statusMapper,
      UserStatusAuditMapper auditMapper,
      RefreshTokenSessionMapper refreshTokenMapper) {
    this.statusMapper = statusMapper;
    this.auditMapper = auditMapper;
    this.refreshTokenMapper = refreshTokenMapper;
  }

  public Optional<UserStatusTarget> findUserForUpdate(long userId) {
    return Optional.ofNullable(statusMapper.findUserForUpdate(userId));
  }

  public List<Long> findActiveAdministratorIdsForUpdate() {
    return statusMapper.findActiveAdministratorIdsForUpdate();
  }

  public List<UserStatusMailAccount> findOwnedMailAccountsForUpdate(long userId) {
    return statusMapper.findOwnedMailAccountsForUpdate(userId);
  }

  public boolean disableUser(
      long userId, long operatedByUserId, String reason, LocalDateTime disabledAt) {
    return statusMapper.disableUser(userId, operatedByUserId, reason, disabledAt) == 1;
  }

  public void disableOwnedMailAccounts(long userId, LocalDateTime disabledAt) {
    statusMapper.disableOwnedMailAccounts(userId, disabledAt);
  }

  public boolean enableUser(long userId, LocalDateTime enabledAt) {
    return statusMapper.enableUser(userId, enabledAt) == 1;
  }

  public void enableOwnedMailAccounts(long userId, LocalDateTime enabledAt) {
    statusMapper.enableOwnedMailAccounts(userId, enabledAt);
  }

  public void revokeRefreshTokens(long userId, LocalDateTime revokedAt) {
    refreshTokenMapper.revokeByUserId(userId, revokedAt);
  }

  public boolean incrementAuthenticationVersion(
      long userId, long expectedVersion, LocalDateTime updatedAt) {
    return statusMapper.incrementAuthenticationVersion(userId, expectedVersion, updatedAt) == 1;
  }

  public void saveAudit(UserStatusAuditEntity audit) {
    if (auditMapper.insert(audit) != 1) {
      throw new IllegalStateException("Could not save user status audit");
    }
  }
}
