package com.yxoct.mail.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class EmailHtmlSanitizer {

  private static final Safelist SAFE_LIST =
      Safelist.relaxed()
          .removeProtocols("a", "href", "ftp")
          .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

  public String sanitize(String html) {
    if (html == null) {
      return null;
    }

    Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
    String sanitized = Jsoup.clean(html, "", SAFE_LIST, outputSettings);
    Document document = Jsoup.parseBodyFragment(sanitized);
    document.outputSettings(outputSettings);
    document.select("img").forEach(image -> image.removeAttr("src"));
    return document.body().html();
  }
}
