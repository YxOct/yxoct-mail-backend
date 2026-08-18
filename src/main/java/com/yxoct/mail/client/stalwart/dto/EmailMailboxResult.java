package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;

public record EmailMailboxResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(String id, Map<String, Boolean> mailboxIds) {}
}
