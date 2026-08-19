package com.yxoct.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monitoring.prometheus")
public record PrometheusProperties(String scrapeToken) {

  public PrometheusProperties {
    scrapeToken = scrapeToken == null ? "" : scrapeToken;
  }
}
