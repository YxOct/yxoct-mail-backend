package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;
import java.time.LocalDateTime;

public record AdminMailAccountProvisioningTarget(
    long mailAccountId,
    long userId,
    MailAccountStatus status,
    LocalDateTime provisioningLeaseUntil) {}
