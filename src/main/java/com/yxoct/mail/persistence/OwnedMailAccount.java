package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record OwnedMailAccount(
    long mailAccountId, String stalwartAccountId, String displayName, MailAccountStatus status) {}
