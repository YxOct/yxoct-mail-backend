package com.yxoct.mail.persistence;

import com.yxoct.mail.persistence.entity.MailAccountStatus;

public record UserStatusMailAccount(
    long mailAccountId, String stalwartAccountId, MailAccountStatus status) {}
