package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.LocalDateTime;

public record AdminMailAccountDriftRecord(
    long mailAccountId,
    long userId,
    String emailAddress,
    MailAccountStatus localStatus,
    String stalwartAccountId,
    String driftType,
    String lastError,
    LocalDateTime checkedAt) {}
