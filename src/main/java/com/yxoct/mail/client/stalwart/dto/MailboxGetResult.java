package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record MailboxGetResult(
    String accountId, String state, List<MailboxInfo> list, List<String> notFound) {

  public record MailboxInfo(
      String id, String name, String role, Long unreadEmails, Long totalEmails, Long sortOrder) {

    public MailboxInfo(String id, String name, String role) {
      this(id, name, role, null, null, null);
    }
  }
}
