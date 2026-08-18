package com.yxoct.mail.domain.mail;

public record MailQueryFilter(String keyword, Boolean read, Boolean starred) {

  public MailQueryFilter {
    keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  public static MailQueryFilter none() {
    return new MailQueryFilter(null, null, null);
  }
}
