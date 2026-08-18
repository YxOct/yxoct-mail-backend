package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailListResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(
      String id,
      String subject,
      String preview,
      String receivedAt,
      Map<String, Boolean> keywords) {}
}
