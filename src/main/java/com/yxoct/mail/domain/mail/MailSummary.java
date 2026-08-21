package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "EmailSummaryResponse", description = "Email summary returned in a mailbox page")
public record MailSummary(
    String id,
    List<String> mailboxIds,
    String subject,
    String preview,
    List<MailAddress> from,
    List<MailAddress> to,
    String receivedAt,
    @Schema(nullable = true) String sentAt,
    boolean read,
    boolean starred,
    boolean hasAttachment,
    long size) {}
