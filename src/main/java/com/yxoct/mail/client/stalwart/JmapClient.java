package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.config.StalwartProperties;
import java.util.List;
import java.util.Map;
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

  public JmapResponse queryEmails(JmapSession session, String username, String password) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/query", Map.of("accountId", accountId, "limit", 20), "0")));

    return RestClient.create()
        .post()
        .uri(session.apiUrl())
        .headers(headers -> headers.setBasicAuth(username, password))
        .body(request)
        .retrieve()
        .body(JmapResponse.class);
  }
}
