package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.time.LocalDateTime;

public record AdminUserRecord(
    long userId,
    String primaryEmailAddress,
    String displayName,
    UserRole role,
    UserStatus userStatus,
    Long mailAccountId,
    MailAccountStatus mailAccountStatus,
    LocalDateTime createdAt) {}
