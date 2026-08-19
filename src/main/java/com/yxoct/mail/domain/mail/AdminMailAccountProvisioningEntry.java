package com.yxoct.mail.domain.mail;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.LocalDateTime;

public record AdminMailAccountProvisioningEntry(
    long mailAccountId,
    long userId,
    String emailAddress,
    MailAccountStatus status,
    int provisioningAttempts,
    String lastProvisioningError,
    LocalDateTime nextProvisioningAt,
    LocalDateTime provisioningLeaseUntil,
    LocalDateTime updatedAt) {}
