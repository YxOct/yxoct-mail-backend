package com.yxoct.mail.domain.mail;

import java.util.List;

public record MailDetail(
    String id,
    String subject,
    String preview,
    String receivedAt,
    List<MailAddress> from,
    List<MailAddress> to,
    String body,
    String textBody,
    String htmlBody,
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
