package com.yxoct.mail.domain.mail;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MailboxRole", description = "Stable mailbox role used by clients")
public enum MailboxRole {
  INBOX,
  SENT,
  DRAFTS,
  TRASH,
  OTHER;

  public static MailboxRole fromJmapRole(String role) {
    if (role == null) {
      return OTHER;
    }
    return switch (role.toLowerCase(java.util.Locale.ROOT)) {
      case "inbox" -> INBOX;
      case "sent" -> SENT;
      case "drafts" -> DRAFTS;
      case "trash" -> TRASH;
      default -> OTHER;
    };
  }
}
