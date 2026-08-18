package com.yxoct.mail.persistence;

public record MailAccountProvisioningTask(
    long accountId,
    String emailAddress,
    String displayName,
    String stalwartAccountId,
    String credentialCiphertext,
    int provisioningAttempts) {}
