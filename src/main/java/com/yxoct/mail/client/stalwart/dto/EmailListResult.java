package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailListResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(
      String id,
      Map<String, Boolean> mailboxIds,
      String subject,
      String preview,
      List<EmailAddress> from,
      List<EmailAddress> to,
      String receivedAt,
      String sentAt,
      Boolean hasAttachment,
      Long size,
      Map<String, Boolean> keywords) {

    public EmailInfo(
        String id,
        String subject,
        String preview,
        String receivedAt,
        Map<String, Boolean> keywords) {
      this(
          id,
          Map.of(),
          subject,
          preview,
          List.of(),
          List.of(),
          receivedAt,
          null,
          false,
          0L,
          keywords);
    }
  }
}
