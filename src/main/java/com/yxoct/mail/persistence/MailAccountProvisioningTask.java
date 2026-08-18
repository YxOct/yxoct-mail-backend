package com.yxoct.mail.persistence;

public record MailAccountProvisioningTask(
    long accountId,
    String emailAddress,
    String stalwartAccountId,
    String credentialCiphertext,
    int provisioningAttempts) {}
