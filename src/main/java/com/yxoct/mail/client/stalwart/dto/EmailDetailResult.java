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
      List<Map<String, Object>> from,
      List<Map<String, Object>> to,
      Map<String, Object> bodyValues,
      List<Map<String, Object>> textBody,
      List<Map<String, Object>> htmlBody) {}
}
