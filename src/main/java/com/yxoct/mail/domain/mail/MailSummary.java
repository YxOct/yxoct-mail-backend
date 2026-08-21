package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailSummaryResponse", description = "Email summary returned in a mailbox page")
public record MailSummary(
    String id, String subject, String preview, String receivedAt, boolean read, boolean starred) {}
