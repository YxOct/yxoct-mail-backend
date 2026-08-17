package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.config.StalwartProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JmapClient {

  private final RestClient restClient;

  public JmapClient(RestClient.Builder builder, StalwartProperties properties) {
    this.restClient = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  public JmapSession getSession(String username, String password) {
    return restClient
        .get()
        .uri("/.well-known/jmap")
        .headers(headers -> headers.setBasicAuth(username, password))
        .retrieve()
        .body(JmapSession.class);
  }
}
