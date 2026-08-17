package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailListResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(String id, String subject, String preview, String receivedAt) {}
}
