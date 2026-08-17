package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapMethodResponse;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.StalwartProperties;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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

    JmapSession session =
        executeRequest(
            () ->
                restClient
                    .get()
                    .uri("/.well-known/jmap")
                    .headers(
                        headers ->
                            headers.setBasicAuth(properties.username(), properties.password()))
                    .retrieve()
                    .body(JmapSession.class));

    validateSession(session);
    return session;
  }

  public EmailQueryResult queryEmails(
      JmapSession session, String mailboxId, int position, int limit) {

    String accountId = getMailAccountId(session);

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
                        "sort",
                        List.of(Map.of("property", "receivedAt", "isAscending", false)),
                        "position",
                        position,
                        "limit",
                        limit,
                        "calculateTotal",
                        true),
                    "0")));

    return convertResponse(invoke(session, request, "Email/query"), EmailQueryResult.class);
  }

  public EmailListResult getEmailSummaries(JmapSession session, List<String> ids) {

    String accountId = getMailAccountId(session);

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

    return convertResponse(invoke(session, request, "Email/get"), EmailListResult.class);
  }

  public EmailDetailResult getEmailDetails(JmapSession session, List<String> ids) {

    String accountId = getMailAccountId(session);

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
                        List.of(
                            "id",
                            "subject",
                            "preview",
                            "receivedAt",
                            "from",
                            "to",
                            "bodyValues",
                            "textBody",
                            "htmlBody"),
                        "fetchTextBodyValues",
                        true,
                        "fetchHTMLBodyValues",
                        true),
                    "0")));

    return convertResponse(invoke(session, request, "Email/get"), EmailDetailResult.class);
  }

  public MailboxGetResult getMailboxes(JmapSession session) {

    String accountId = getMailAccountId(session);

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(new JmapMethodCall("Mailbox/get", Map.of("accountId", accountId), "0")));

    return convertResponse(invoke(session, request, "Mailbox/get"), MailboxGetResult.class);
  }

  private JsonNode invoke(JmapSession session, JmapRequest request, String expectedMethod) {

    validateSession(session);

    JmapResponse response = post(session, request);

    if (response == null
        || response.methodResponses() == null
        || response.methodResponses().size() != 1) {
      throw mailServiceUnavailable();
    }

    JmapMethodResponse methodResponse = response.methodResponses().getFirst();

    if (methodResponse == null
        || "error".equals(methodResponse.method())
        || !expectedMethod.equals(methodResponse.method())
        || !"0".equals(methodResponse.callId())
        || methodResponse.response() == null) {
      throw mailServiceUnavailable();
    }

    return methodResponse.response();
  }

  private void validateSession(JmapSession session) {

    if (session == null || session.apiUrl() == null || session.primaryAccounts() == null) {
      throw mailServiceUnavailable();
    }

    String accountId = session.primaryAccounts().get("urn:ietf:params:jmap:mail");

    if (accountId == null || accountId.isBlank()) {
      throw mailServiceUnavailable();
    }
  }

  private String getMailAccountId(JmapSession session) {
    validateSession(session);
    return session.primaryAccounts().get("urn:ietf:params:jmap:mail");
  }

  private BusinessException mailServiceUnavailable() {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  private BusinessException mailServiceUnavailable(Throwable cause) {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, cause);
  }

  private <T> T executeRequest(Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientException exception) {
      throw mailServiceUnavailable(exception);
    }
  }

  private <T> T convertResponse(JsonNode response, Class<T> responseType) {
    try {
      return objectMapper.convertValue(response, responseType);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw mailServiceUnavailable(exception);
    }
  }

  private JmapResponse post(JmapSession session, JmapRequest request) {

    return executeRequest(
        () ->
            restClient
                .post()
                .uri(session.apiUrl())
                .headers(
                    headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .body(request)
                .retrieve()
                .body(JmapResponse.class));
  }
}
