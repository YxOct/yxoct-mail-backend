package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.LocalDateTime;

public record AdminMailAccountProvisioningRecord(
    long mailAccountId,
    long userId,
    String emailAddress,
    MailAccountStatus status,
    int provisioningAttempts,
    String lastProvisioningError,
    LocalDateTime nextProvisioningAt,
    LocalDateTime provisioningLeaseUntil,
    LocalDateTime updatedAt) {}
