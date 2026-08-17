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
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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

    EmailQueryResult result =
        convertResponse(invoke(session, request, "Email/query"), EmailQueryResult.class);

    if (result == null
        || !accountId.equals(result.accountId())
        || result.queryState() == null
        || result.queryState().isBlank()
        || result.position() != position
        || result.total() == null
        || result.total() < 0
        || !hasUniqueIds(result.ids())) {
      throw mailServiceUnavailable();
    }

    return result;
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

    EmailListResult result =
        convertResponse(invoke(session, request, "Email/get"), EmailListResult.class);
    validateGetResult(
        accountId,
        result == null ? null : result.accountId(),
        result == null ? null : result.state(),
        result == null ? null : result.list(),
        result == null ? null : result.notFound(),
        ids,
        EmailListResult.EmailInfo::id);
    return result;
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

    EmailDetailResult result =
        convertResponse(invoke(session, request, "Email/get"), EmailDetailResult.class);
    validateGetResult(
        accountId,
        result == null ? null : result.accountId(),
        result == null ? null : result.state(),
        result == null ? null : result.list(),
        result == null ? null : result.notFound(),
        ids,
        EmailDetailResult.EmailInfo::id);
    return result;
  }

  public MailboxGetResult getMailboxes(JmapSession session) {

    String accountId = getMailAccountId(session);

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(new JmapMethodCall("Mailbox/get", Map.of("accountId", accountId), "0")));

    MailboxGetResult result =
        convertResponse(invoke(session, request, "Mailbox/get"), MailboxGetResult.class);

    if (result == null
        || !accountId.equals(result.accountId())
        || result.state() == null
        || result.state().isBlank()
        || result.list() == null
        || result.list().stream().anyMatch(Objects::isNull)
        || !hasUniqueIds(result.list().stream().map(MailboxGetResult.MailboxInfo::id).toList())
        || result.notFound() == null
        || !result.notFound().isEmpty()) {
      throw mailServiceUnavailable();
    }

    return result;
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

    if (session == null
        || !isValidApiUrl(session.apiUrl())
        || session.primaryAccounts() == null
        || session.state() == null
        || session.state().isBlank()) {
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

  private boolean isValidApiUrl(URI apiUrl) {
    return apiUrl != null
        && apiUrl.isAbsolute()
        && apiUrl.getHost() != null
        && ("http".equalsIgnoreCase(apiUrl.getScheme())
            || "https".equalsIgnoreCase(apiUrl.getScheme()));
  }

  private boolean hasUniqueIds(List<String> ids) {
    return ids != null
        && ids.stream().allMatch(id -> id != null && !id.isBlank())
        && new HashSet<>(ids).size() == ids.size();
  }

  private <T> void validateGetResult(
      String expectedAccountId,
      String actualAccountId,
      String state,
      List<T> list,
      List<String> notFound,
      List<String> requestedIds,
      Function<T, String> idExtractor) {

    if (!expectedAccountId.equals(actualAccountId)
        || state == null
        || state.isBlank()
        || list == null
        || list.stream().anyMatch(Objects::isNull)
        || notFound == null
        || !hasUniqueIds(requestedIds)
        || !hasUniqueIds(notFound)) {
      throw mailServiceUnavailable();
    }

    List<String> foundIds = list.stream().map(idExtractor).toList();
    if (!hasUniqueIds(foundIds)) {
      throw mailServiceUnavailable();
    }

    Set<String> requested = Set.copyOf(requestedIds);
    Set<String> returned = new HashSet<>(foundIds);
    if (notFound.stream().anyMatch(returned::contains)) {
      throw mailServiceUnavailable();
    }

    returned.addAll(notFound);
    if (!returned.equals(requested)) {
      throw mailServiceUnavailable();
    }
  }

  private BusinessException mailServiceUnavailable() {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  private BusinessException mailServiceUnavailable(Throwable cause) {
    return new BusinessException(ErrorCode.MAIL_SERVICE_UNAVAILABLE, cause);
  }

  private BusinessException mapClientException(RestClientException exception) {
    if (exception instanceof ResourceAccessException
        && (hasCause(exception, SocketTimeoutException.class)
            || hasCause(exception, HttpTimeoutException.class))) {
      return new BusinessException(ErrorCode.MAIL_SERVICE_TIMEOUT, exception);
    }

    if (exception instanceof RestClientResponseException responseException
        && (responseException.getStatusCode().value() == 401
            || responseException.getStatusCode().value() == 403)) {
      return new BusinessException(ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED, exception);
    }

    return mailServiceUnavailable(exception);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  private <T> T executeRequest(Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientException exception) {
      throw mapClientException(exception);
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
