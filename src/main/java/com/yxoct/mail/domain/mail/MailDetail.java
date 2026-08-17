package com.yxoct.mail.domain.mail;

import java.time.Instant;

public record MailDetail(String id, String subject, String preview, Instant receivedAt) {}
