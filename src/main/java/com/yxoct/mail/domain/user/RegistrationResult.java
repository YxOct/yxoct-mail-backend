package com.yxoct.mail.domain.user;

import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record RegistrationResult(
    long userId, long mailAccountId, String emailAddress, MailAccountStatus status) {}
