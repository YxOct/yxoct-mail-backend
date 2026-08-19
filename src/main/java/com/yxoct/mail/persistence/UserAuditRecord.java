package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.UserStatusAuditAction;
import java.time.LocalDateTime;

public record UserAuditRecord(
    long auditId,
    UserStatusAuditAction action,
    String reason,
    Long operatedByUserId,
    String operatedByEmailAddress,
    LocalDateTime createdAt) {}
