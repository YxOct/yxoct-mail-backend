package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.UserStatusAuditEntity;
import com.yxoct.mail.persistence.mapper.RefreshTokenSessionMapper;
import com.yxoct.mail.persistence.mapper.UserPasswordMapper;
import com.yxoct.mail.persistence.mapper.UserStatusAuditMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserPasswordRepository {

  private final UserPasswordMapper passwordMapper;
  private final RefreshTokenSessionMapper refreshTokenMapper;
  private final UserStatusAuditMapper auditMapper;

  public UserPasswordRepository(
      UserPasswordMapper passwordMapper,
      RefreshTokenSessionMapper refreshTokenMapper,
      UserStatusAuditMapper auditMapper) {
    this.passwordMapper = passwordMapper;
    this.refreshTokenMapper = refreshTokenMapper;
    this.auditMapper = auditMapper;
  }

  public Optional<PasswordChangeTarget> findForUpdate(long userId) {
    return Optional.ofNullable(passwordMapper.findForUpdate(userId));
  }

  public boolean updatePassword(
      long userId,
      long expectedVersion,
      String passwordHash,
      boolean mustChangePassword,
      LocalDateTime updatedAt) {
    return passwordMapper.updatePassword(
            userId, expectedVersion, passwordHash, mustChangePassword, updatedAt)
        == 1;
  }

  public void revokeRefreshTokens(long userId, LocalDateTime revokedAt) {
    refreshTokenMapper.revokeByUserId(userId, revokedAt);
  }

  public void saveAudit(UserStatusAuditEntity audit) {
    if (auditMapper.insert(audit) != 1) {
      throw new IllegalStateException("Could not save password change audit");
    }
  }
}
