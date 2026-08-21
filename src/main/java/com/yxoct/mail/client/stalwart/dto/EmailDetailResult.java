package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailDetailResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(
      String id,
      Map<String, Boolean> mailboxIds,
      String subject,
      String preview,
      String receivedAt,
      String sentAt,
      List<EmailAddress> from,
      List<EmailAddress> to,
      List<EmailAddress> cc,
      List<EmailAddress> bcc,
      Map<String, EmailBodyValue> bodyValues,
      List<EmailBodyPart> textBody,
      List<EmailBodyPart> htmlBody,
      List<EmailBodyPart> attachments,
      Map<String, Boolean> keywords) {

    public EmailInfo(
        String id,
        String subject,
        String preview,
        String receivedAt,
        List<EmailAddress> from,
        List<EmailAddress> to,
        Map<String, EmailBodyValue> bodyValues,
        List<EmailBodyPart> textBody,
        List<EmailBodyPart> htmlBody,
        List<EmailBodyPart> attachments,
        Map<String, Boolean> keywords) {
      this(
          id,
          Map.of(),
          subject,
          preview,
          receivedAt,
          null,
          from,
          to,
          List.of(),
          List.of(),
          bodyValues,
          textBody,
          htmlBody,
          attachments,
          keywords);
    }
  }
}
