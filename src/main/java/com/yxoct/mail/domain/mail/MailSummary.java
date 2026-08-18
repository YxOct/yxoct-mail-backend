package com.yxoct.mail.domain.mail;

public record MailSummary(
    String id, String subject, String preview, String receivedAt, boolean read, boolean starred) {}
