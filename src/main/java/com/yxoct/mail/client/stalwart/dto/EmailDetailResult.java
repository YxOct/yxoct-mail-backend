package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailDetailResult(List<EmailInfo> list) {

  public record EmailInfo(
      String id,
      String subject,
      String preview,
      String receivedAt,
      Map<String, Object> bodyValues,
      List<Map<String, Object>> from,
      List<Map<String, Object>> to) {}
}
