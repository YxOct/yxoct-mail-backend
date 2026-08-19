package com.yxoct.mail.persistence.entity;

public enum UserStatusAuditAction {
  DISABLED,
  ENABLED,
  FORCED_LOGOUT,
  PASSWORD_CHANGED,
  PASSWORD_RESET_BY_ADMIN
}
