package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;

public record CurrentUserResponse(
    long userId,
    long mailAccountId,
    String emailAddress,
    String displayName,
    UserRole role,
    UserStatus status,
    MailAccountStatus mailAccountStatus,
    boolean mustChangePassword) {
  public CurrentUserResponse(
      long userId,
      long mailAccountId,
      String emailAddress,
      String displayName,
      UserRole role,
      UserStatus status,
      MailAccountStatus mailAccountStatus) {
    this(userId, mailAccountId, emailAddress, displayName, role, status, mailAccountStatus, false);
  }
}
