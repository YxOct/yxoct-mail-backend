package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record AdminMailAccountDriftTarget(
    long mailAccountId,
    long userId,
    String emailAddress,
    String displayName,
    String stalwartAccountId,
    MailAccountStatus localStatus,
    MailAccountDriftType driftType) {}
