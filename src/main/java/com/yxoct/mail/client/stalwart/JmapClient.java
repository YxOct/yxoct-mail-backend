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
  private final StalwartProperties properties;

  public JmapClient(RestClient.Builder builder, StalwartProperties properties) {
    this.properties = properties;
    this.restClient = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  public JmapSession getSession() {
    return restClient
        .get()
        .uri("/.well-known/jmap")
        .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
        .retrieve()
        .body(JmapSession.class);
  }

  public JmapResponse queryEmails(JmapSession session) {

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
        .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
        .body(request)
        .retrieve()
        .body(JmapResponse.class);
  }

  public JmapResponse getEmails(
      JmapSession session, List<String> ids) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall("Email/get", Map.of("accountId", accountId, "ids", ids), "0")));

    return RestClient.create()
        .post()
        .uri(session.apiUrl())
        .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
        .body(request)
        .retrieve()
        .body(JmapResponse.class);
  }
}
