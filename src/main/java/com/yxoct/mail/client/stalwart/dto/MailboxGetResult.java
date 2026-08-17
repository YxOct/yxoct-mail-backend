package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record MailboxGetResult(List<MailboxInfo> list) {

  public record MailboxInfo(String id, String name, String role) {}
}
