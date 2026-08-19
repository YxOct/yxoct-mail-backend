package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;
import java.time.LocalDateTime;

public record AdminUserSummary(
    long userId,
    String primaryEmailAddress,
    String displayName,
    UserRole role,
    UserStatus userStatus,
    Long mailAccountId,
    MailAccountStatus mailAccountStatus,
    LocalDateTime createdAt) {}
