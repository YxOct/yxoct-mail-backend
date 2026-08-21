package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    name = "EmailDetailResponse",
    description = "Email detail including safe body content and attachments")
public record MailDetail(
    String id,
    String subject,
    String preview,
    String receivedAt,
    List<MailAddress> from,
    List<MailAddress> to,
    @Schema(nullable = true) String body,
    @Schema(nullable = true) String textBody,
    @Schema(nullable = true) String htmlBody,
    boolean read,
    boolean starred,
    List<MailAttachment> attachments) {

  public MailDetail(
      String id,
      String subject,
      String preview,
      String receivedAt,
      List<MailAddress> from,
      List<MailAddress> to,
      String body,
      boolean read,
      boolean starred,
      List<MailAttachment> attachments) {
    this(id, subject, preview, receivedAt, from, to, body, body, null, read, starred, attachments);
  }
}
