package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailListResult(List<EmailInfo> list) {

  public record EmailInfo(String id, String subject, String preview, String receivedAt) {}
}
