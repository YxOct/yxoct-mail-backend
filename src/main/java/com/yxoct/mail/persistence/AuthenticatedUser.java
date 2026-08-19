package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;

public record AuthenticatedUser(
    long userId,
    String emailAddress,
    String passwordHash,
    UserStatus status,
    UserRole role,
    long version,
    boolean mustChangePassword) {
  public AuthenticatedUser(
      long userId,
      String emailAddress,
      String passwordHash,
      UserStatus status,
      UserRole role,
      long version) {
    this(userId, emailAddress, passwordHash, status, role, version, false);
  }
}
