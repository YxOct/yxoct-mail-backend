package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import java.time.LocalDateTime;

public record AdminUserAuditEntry(
    long auditId,
    UserStatusAuditAction action,
    String reason,
    Long operatedByUserId,
    String operatedByEmailAddress,
    LocalDateTime createdAt) {}
