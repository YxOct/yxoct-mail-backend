package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import com.yxoct.mail.persistence.entity.UserRole;
import com.yxoct.mail.persistence.entity.UserStatus;

public record CurrentUserAccount(
    long userId,
    long mailAccountId,
    String emailAddress,
    String displayName,
    UserRole role,
    UserStatus status,
    MailAccountStatus mailAccountStatus) {}
