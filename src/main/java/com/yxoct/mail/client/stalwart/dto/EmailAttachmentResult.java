package com.yxoct.mail.client.stalwart.dto;

import java.util.List;

public record EmailAttachmentResult(
    String accountId, String state, List<EmailInfo> list, List<String> notFound) {

  public record EmailInfo(String id, List<EmailBodyPart> attachments) {}
}
