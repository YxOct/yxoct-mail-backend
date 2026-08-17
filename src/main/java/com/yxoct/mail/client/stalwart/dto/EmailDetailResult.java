package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailDetailResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(
      String id,
      String subject,
      String preview,
      String receivedAt,
      List<EmailAddress> from,
      List<EmailAddress> to,
      Map<String, EmailBodyValue> bodyValues,
      List<EmailBodyPart> textBody,
      List<EmailBodyPart> htmlBody) {}
}
