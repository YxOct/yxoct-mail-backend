package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.EmailSetResult;
import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapMethodResponse;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.client.stalwart.dto.JmapResponse;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.client.stalwart.dto.MailboxGetResult;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.common.web.RequestIdContext;
import com.yxoct.mail.config.StalwartProperties;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
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
  private final StalwartClientMetrics metrics;

  public JmapClient(
      RestClient.Builder builder,
      StalwartProperties properties,
      ObjectMapper objectMapper,
      StalwartClientMetrics metrics) {

    this.properties = properties;
    this.objectMapper = objectMapper;
    this.metrics = metrics;

    this.restClient = builder.baseUrl(properties.baseUrl().toString()).build();
  }

  public JmapSession getSession() {
    return metrics.record("session", this::fetchSession);
  }

  private JmapSession fetchSession() {

    JmapSession session =
        executeRequest(
            () ->
                restClient
                    .get()
                    .uri("/.well-known/jmap")
                    .headers(this::setRequestHeaders)
                    .retrieve()
                    .body(JmapSession.class));

    validateSession(session);
    return session;
  }

  public EmailQueryResult queryEmails(
      JmapSession session, String mailboxId, int position, int limit) {
    return metrics.record(
        "email.query", () -> queryEmailsInternal(session, mailboxId, position, limit));
  }

  private EmailQueryResult queryEmailsInternal(
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
    return metrics.record("email.summaries", () -> getEmailSummariesInternal(session, ids));
  }

  private EmailListResult getEmailSummariesInternal(JmapSession session, List<String> ids) {

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
                        List.of("id", "subject", "preview", "receivedAt", "keywords")),
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
    return metrics.record("email.detail", () -> getEmailDetailsInternal(session, ids));
  }

  private EmailDetailResult getEmailDetailsInternal(JmapSession session, List<String> ids) {

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
                            "htmlBody",
                            "keywords"),
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

  public void setEmailRead(JmapSession session, String emailId, boolean read) {
    metrics.record(
        "email.read-status",
        () -> {
          updateEmailKeyword(session, emailId, "$seen", read);
          return null;
        });
  }

  private void updateEmailKeyword(
      JmapSession session, String emailId, String keyword, boolean enabled) {

    String accountId = getMailAccountId(session);
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("keywords/" + keyword, enabled ? true : null);

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/set",
                    Map.of("accountId", accountId, "update", Map.of(emailId, patch)),
                    "0")));

    EmailSetResult result =
        convertResponse(invoke(session, request, "Email/set"), EmailSetResult.class);
    validateEmailUpdateResult(accountId, emailId, result);
  }

  private void validateEmailUpdateResult(String accountId, String emailId, EmailSetResult result) {

    if (result == null
        || !accountId.equals(result.accountId())
        || result.oldState() == null
        || result.oldState().isBlank()
        || result.newState() == null
        || result.newState().isBlank()) {
      throw mailServiceUnavailable();
    }

    Map<String, JsonNode> updated = result.updated() == null ? Map.of() : result.updated();
    Map<String, EmailSetResult.SetError> notUpdated =
        result.notUpdated() == null ? Map.of() : result.notUpdated();

    if (!updated.keySet().stream().allMatch(emailId::equals)
        || !notUpdated.keySet().stream().allMatch(emailId::equals)
        || updated.containsKey(emailId) == notUpdated.containsKey(emailId)) {
      throw mailServiceUnavailable();
    }

    if (updated.containsKey(emailId)) {
      return;
    }

    EmailSetResult.SetError error = notUpdated.get(emailId);
    if (error != null && "notFound".equals(error.type())) {
      throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
    }
    throw mailServiceUnavailable();
  }

  public MailboxGetResult getMailboxes(JmapSession session) {
    return metrics.record("mailbox.list", () -> getMailboxesInternal(session));
  }

  private MailboxGetResult getMailboxesInternal(JmapSession session) {

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
                .headers(this::setRequestHeaders)
                .body(request)
                .retrieve()
                .body(JmapResponse.class));
  }

  private void setRequestHeaders(HttpHeaders headers) {
    headers.setBasicAuth(properties.username(), properties.password());
    String requestId = RequestIdContext.current();
    if (requestId != null) {
      headers.set(RequestIdContext.HEADER_NAME, requestId);
    }
  }
}
