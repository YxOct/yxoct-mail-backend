package com.yxoct.mail.service;

import com.yxoct.mail.domain.mail.MailAttachment;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class EmailHtmlSanitizer {

  private static final Safelist SAFE_LIST =
      Safelist.relaxed()
          .removeProtocols("a", "href", "ftp")
          .addProtocols("img", "src", "cid")
          .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

  public String sanitize(String html) {
    return sanitize(html, null, List.of());
  }

  public String sanitize(String html, String emailId, List<MailAttachment> attachments) {
    if (html == null) {
      return null;
    }

    Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
    String sanitized = Jsoup.clean(html, "", SAFE_LIST, outputSettings);
    Document document = Jsoup.parseBodyFragment(sanitized);
    document.outputSettings(outputSettings);
    document
        .select("img")
        .forEach(
            image -> {
              String source = image.attr("src");
              MailAttachment attachment = findCidAttachment(source, attachments);
              if (emailId == null || emailId.isBlank() || attachment == null) {
                image.removeAttr("src");
                return;
              }
              image.attr(
                  "src",
                  "/api/mail/emails/"
                      + UriUtils.encodePathSegment(emailId, StandardCharsets.UTF_8)
                      + "/attachments/"
                      + UriUtils.encodePathSegment(attachment.blobId(), StandardCharsets.UTF_8));
            });
    return document.body().html();
  }

  private MailAttachment findCidAttachment(String source, List<MailAttachment> attachments) {
    if (source == null || !source.regionMatches(true, 0, "cid:", 0, 4) || attachments == null) {
      return null;
    }

    String cid;
    try {
      cid = UriUtils.decode(source.substring(4), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      return null;
    }

    return attachments.stream()
        .filter(attachment -> attachment != null && attachment.cid() != null)
        .filter(attachment -> attachment.cid().equalsIgnoreCase(cid))
        .findFirst()
        .orElse(null);
  }
}
