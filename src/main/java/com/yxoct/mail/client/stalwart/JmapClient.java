package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.EmailGetResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
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

  @SuppressWarnings("unchecked")
  public EmailQueryResult queryEmails(JmapSession session) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/query", Map.of("accountId", accountId, "limit", 20), "0")));

    JmapResponse response =
        restClient
            .post()
            .uri(session.apiUrl())
            .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
            .body(request)
            .retrieve()
            .body(JmapResponse.class);

    Object[] methodResponse = response.methodResponses().get(0);

    Map<String, Object> result = (Map<String, Object>) methodResponse[1];

    return new EmailQueryResult((List<String>) result.get("ids"));
  }

  @SuppressWarnings("unchecked")
  public EmailGetResult getEmails(JmapSession session, List<String> ids) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall("Email/get", Map.of("accountId", accountId, "ids", ids), "0")));

    JmapResponse response =
        restClient
            .post()
            .uri(session.apiUrl())
            .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
            .body(request)
            .retrieve()
            .body(JmapResponse.class);

    Object[] methodResponse = response.methodResponses().get(0);

    Map<String, Object> result = (Map<String, Object>) methodResponse[1];

    return new EmailGetResult((List<Map<String, Object>>) result.get("list"));
  }
}
