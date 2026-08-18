package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record MailAccountCredential(
    long userId,
    long mailAccountId,
    String emailAddress,
    MailAccountStatus status,
    String credentialCiphertext) {}
