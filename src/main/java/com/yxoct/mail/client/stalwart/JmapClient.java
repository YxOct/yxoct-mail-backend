package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.config.StalwartProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
public class JmapClient {

  private final RestClient restClient;
  private final StalwartProperties properties;
  private final ObjectMapper objectMapper;

  public JmapClient(
      RestClient.Builder builder, StalwartProperties properties, ObjectMapper objectMapper) {

    this.properties = properties;
    this.objectMapper = objectMapper;

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

  public EmailQueryResult queryEmails(
      JmapSession session, String mailboxId, int position, int limit) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/query",
                    Map.of(
                        "accountId",
                        accountId,
                        "filter",
                        Map.of("inMailbox", mailboxId),
                        "position",
                        position,
                        "limit",
                        limit,
                        "calculateTotal",
                        true),
                    "0")));

    JmapResponse response = post(session, request);

    return objectMapper.convertValue(
        response.methodResponses().get(0).response(), EmailQueryResult.class);
  }

  public EmailListResult getEmailSummaries(JmapSession session, List<String> ids) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/get",
                    Map.of(
                        "accountId",
                        accountId,
                        "ids",
                        ids,
                        "properties",
                        List.of("id", "subject", "preview", "receivedAt")),
                    "0")));

    JmapResponse response = post(session, request);

    return objectMapper.convertValue(
        response.methodResponses().get(0).response(), EmailListResult.class);
  }

  public EmailDetailResult getEmailDetails(JmapSession session, List<String> ids) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall("Email/get", Map.of("accountId", accountId, "ids", ids), "0")));

    JmapResponse response = post(session, request);

    return objectMapper.convertValue(
        response.methodResponses().get(0).response(), EmailDetailResult.class);
  }

  public MailboxGetResult getMailboxes(JmapSession session) {

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(new JmapMethodCall("Mailbox/get", Map.of("accountId", accountId), "0")));

    JmapResponse response = post(session, request);

    return objectMapper.convertValue(
        response.methodResponses().get(0).response(), MailboxGetResult.class);
  }

  private JmapResponse post(JmapSession session, JmapRequest request) {

    return restClient
        .post()
        .uri(session.apiUrl())
        .headers(headers -> headers.setBasicAuth(properties.username(), properties.password()))
        .body(request)
        .retrieve()
        .body(JmapResponse.class);
  }
}
