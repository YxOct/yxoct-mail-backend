package com.yxoct.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailHtmlSanitizerTest {

  private final EmailHtmlSanitizer sanitizer = new EmailHtmlSanitizer();

  @Test
  void removesExecutableHtmlAndRemoteImages() {
    String html =
        "<script>alert(1)</script><p onclick=\"alert(2)\">Hello</p>"
            + "<img src=\"https://tracker.example/pixel\" onerror=\"alert(3)\">";

    String sanitized = sanitizer.sanitize(html);

    assertThat(sanitized).isEqualTo("<p>Hello</p><img>");
  }

  @Test
  void hardensLinksAndRejectsFtp() {
    String sanitized =
        sanitizer.sanitize(
            "<a href=\"https://example.com\">safe</a><a href=\"ftp://example.com/a\">ftp</a>");

    assertThat(sanitized)
        .contains("href=\"https://example.com\"")
        .contains("rel=\"nofollow noopener noreferrer\"")
        .doesNotContain("ftp://");
  }
}
