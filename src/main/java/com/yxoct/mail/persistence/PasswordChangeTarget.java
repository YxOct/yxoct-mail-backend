package com.yxoct.mail.persistence;

public record PasswordChangeTarget(
    long userId, String passwordHash, long version, boolean mustChangePassword) {
  public PasswordChangeTarget(long userId, String passwordHash, long version) {
    this(userId, passwordHash, version, false);
  }
}
