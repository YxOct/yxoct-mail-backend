package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record MailAccountReconciliationCandidate(
    long mailAccountId,
    long userId,
    String emailAddress,
    String displayName,
    String stalwartAccountId,
    MailAccountStatus status) {}
