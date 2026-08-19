package com.yxoct.mail.domain.mail;

import com.yxoct.mail.persistence.entity.MailAccountDriftType;
import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.LocalDateTime;

public record AdminMailAccountDriftEntry(
    long mailAccountId,
    long userId,
    String emailAddress,
    MailAccountStatus localStatus,
    String stalwartAccountId,
    MailAccountDriftType driftType,
    String lastError,
    LocalDateTime checkedAt) {}
