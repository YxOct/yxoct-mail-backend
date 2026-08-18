package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.EmailAttachmentResult;
import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailMailboxResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.EmailSetResult;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
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
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JmapClient {

  private final RestClient restClient;
  private final StalwartProperties properties;
  private final StalwartCredentialsProvider credentialsProvider;
  private final ObjectMapper objectMapper;
  private final StalwartClientMetrics metrics;

  public JmapClient(
      RestClient.Builder builder,
      StalwartProperties properties,
      StalwartCredentialsProvider credentialsProvider,
      ObjectMapper objectMapper,
      StalwartClientMetrics metrics) {

    this.properties = properties;
    this.credentialsProvider = credentialsProvider;
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
      JmapSession session,
      String mailboxId,
      int position,
      int limit,
      MailQueryFilter filter,
      MailSort sort) {
    return metrics.record(
        "email.query",
        () -> queryEmailsInternal(session, mailboxId, position, limit, filter, sort));
  }

  private EmailQueryResult queryEmailsInternal(
      JmapSession session,
      String mailboxId,
      int position,
      int limit,
      MailQueryFilter filter,
      MailSort sort) {

    String accountId = getMailAccountId(session);
    if (mailboxId == null || mailboxId.isBlank() || filter == null || sort == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

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
                        buildEmailFilter(mailboxId, filter),
                        "sort",
                        List.of(
                            Map.of(
                                "property",
                                sort.field().jmapProperty(),
                                "isAscending",
                                sort.direction().ascending())),
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

  private Map<String, Object> buildEmailFilter(String mailboxId, MailQueryFilter filter) {
    List<Map<String, Object>> conditions = new ArrayList<>();
    conditions.add(Map.of("inMailbox", mailboxId));
    if (filter.keyword() != null) {
      conditions.add(Map.of("text", filter.keyword()));
    }
    if (filter.read() != null) {
      conditions.add(Map.of(filter.read() ? "hasKeyword" : "notKeyword", "$seen"));
    }
    if (filter.starred() != null) {
      conditions.add(Map.of(filter.starred() ? "hasKeyword" : "notKeyword", "$flagged"));
    }
    if (conditions.size() == 1) {
      return conditions.getFirst();
    }
    return Map.of("operator", "AND", "conditions", conditions);
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
                            "attachments",
                            "keywords"),
                        "bodyProperties",
                        List.of("partId", "blobId", "size", "name", "type", "disposition", "cid"),
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

  public void downloadBlob(
      JmapSession session, String blobId, String name, String type, OutputStream outputStream) {
    metrics.record(
        "blob.download",
        () -> {
          downloadBlobInternal(session, blobId, name, type, outputStream);
          return null;
        });
  }

  public EmailAttachmentResult getEmailAttachments(JmapSession session, List<String> ids) {
    return metrics.record("email.attachments", () -> getEmailAttachmentsInternal(session, ids));
  }

  private EmailAttachmentResult getEmailAttachmentsInternal(JmapSession session, List<String> ids) {
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
                        List.of("id", "attachments"),
                        "bodyProperties",
                        List.of("partId", "blobId", "size", "name", "type", "disposition", "cid")),
                    "0")));

    EmailAttachmentResult result =
        convertResponse(invoke(session, request, "Email/get"), EmailAttachmentResult.class);
    validateGetResult(
        accountId,
        result == null ? null : result.accountId(),
        result == null ? null : result.state(),
        result == null ? null : result.list(),
        result == null ? null : result.notFound(),
        ids,
        EmailAttachmentResult.EmailInfo::id);
    return result;
  }

  private void downloadBlobInternal(
      JmapSession session, String blobId, String name, String type, OutputStream outputStream) {
    String accountId = getMailAccountId(session);
    if (blobId == null
        || blobId.isBlank()
        || type == null
        || type.isBlank()
        || outputStream == null
        || session.downloadUrl() == null
        || session.downloadUrl().isBlank()) {
      throw mailServiceUnavailable();
    }

    URI downloadUri;
    try {
      downloadUri =
          new UriTemplate(session.downloadUrl())
              .expand(
                  Map.of(
                      "accountId", accountId,
                      "blobId", blobId,
                      "type", type,
                      "name", name == null || name.isBlank() ? "attachment" : name));
    } catch (IllegalArgumentException exception) {
      throw mailServiceUnavailable(exception);
    }

    if (!isSameOrigin(downloadUri, properties.baseUrl())) {
      throw mailServiceUnavailable();
    }

    executeRequest(
        () ->
            restClient
                .get()
                .uri(downloadUri)
                .headers(this::setRequestHeaders)
                .exchange(
                    (request, response) -> {
                      HttpStatusCode status = response.getStatusCode();
                      if (status.value() == 401 || status.value() == 403) {
                        throw new BusinessException(ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED);
                      }
                      if (status.value() == 404) {
                        throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
                      }
                      if (status.isError()) {
                        throw mailServiceUnavailable();
                      }
                      try {
                        response.getBody().transferTo(outputStream);
                        outputStream.flush();
                      } catch (IOException exception) {
                        throw mailServiceUnavailable(exception);
                      }
                      return null;
                    }));
  }

  public EmailUpdateResult setEmailsRead(JmapSession session, List<String> emailIds, boolean read) {
    return metrics.record(
        "email.read-status", () -> updateEmailKeyword(session, emailIds, "$seen", read));
  }

  public EmailUpdateResult setEmailsStarred(
      JmapSession session, List<String> emailIds, boolean starred) {
    return metrics.record(
        "email.star-status", () -> updateEmailKeyword(session, emailIds, "$flagged", starred));
  }

  public EmailUpdateResult destroyEmails(JmapSession session, List<String> emailIds) {
    return metrics.record("email.destroy", () -> destroyEmailsInternal(session, emailIds));
  }

  private EmailUpdateResult destroyEmailsInternal(JmapSession session, List<String> emailIds) {
    String accountId = getMailAccountId(session);
    if (emailIds == null || emailIds.isEmpty() || !hasUniqueIds(emailIds)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/set", Map.of("accountId", accountId, "destroy", emailIds), "0")));
    EmailSetResult result =
        convertResponse(invoke(session, request, "Email/set"), EmailSetResult.class);
    return validateEmailDestroyResult(accountId, emailIds, result);
  }

  public EmailMailboxResult getEmailMailboxes(JmapSession session, List<String> emailIds) {
    return metrics.record("email.mailboxes", () -> getEmailMailboxesInternal(session, emailIds));
  }

  private EmailMailboxResult getEmailMailboxesInternal(JmapSession session, List<String> emailIds) {

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
                        emailIds,
                        "properties",
                        List.of("id", "mailboxIds")),
                    "0")));

    EmailMailboxResult result =
        convertResponse(invoke(session, request, "Email/get"), EmailMailboxResult.class);
    validateGetResult(
        accountId,
        result == null ? null : result.accountId(),
        result == null ? null : result.state(),
        result == null ? null : result.list(),
        result == null ? null : result.notFound(),
        emailIds,
        EmailMailboxResult.EmailInfo::id);

    if (result.list().stream().anyMatch(email -> !isValidMailboxIds(email.mailboxIds()))) {
      throw mailServiceUnavailable();
    }
    return result;
  }

  public EmailUpdateResult setEmailMailboxes(
      JmapSession session, Map<String, List<String>> mailboxIdsByEmail) {
    return metrics.record(
        "email.move", () -> setEmailMailboxesInternal(session, mailboxIdsByEmail));
  }

  private EmailUpdateResult setEmailMailboxesInternal(
      JmapSession session, Map<String, List<String>> mailboxIdsByEmail) {

    String accountId = getMailAccountId(session);
    if (mailboxIdsByEmail == null
        || mailboxIdsByEmail.isEmpty()
        || !hasUniqueIds(List.copyOf(mailboxIdsByEmail.keySet()))
        || mailboxIdsByEmail.values().stream()
            .anyMatch(
                mailboxIds ->
                    mailboxIds == null || mailboxIds.isEmpty() || !hasUniqueIds(mailboxIds))) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    Map<String, Map<String, Object>> updates = new LinkedHashMap<>();
    mailboxIdsByEmail.forEach(
        (emailId, mailboxIds) -> {
          Map<String, Boolean> targetMailboxIds = new LinkedHashMap<>();
          mailboxIds.forEach(mailboxId -> targetMailboxIds.put(mailboxId, true));
          updates.put(emailId, Map.of("mailboxIds", targetMailboxIds));
        });

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/set", Map.of("accountId", accountId, "update", updates), "0")));

    EmailSetResult result =
        convertResponse(invoke(session, request, "Email/set"), EmailSetResult.class);
    return validateEmailUpdateResult(accountId, List.copyOf(mailboxIdsByEmail.keySet()), result);
  }

  private EmailUpdateResult updateEmailKeyword(
      JmapSession session, List<String> emailIds, String keyword, boolean enabled) {

    String accountId = getMailAccountId(session);
    if (emailIds == null || emailIds.isEmpty() || !hasUniqueIds(emailIds)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    Map<String, Map<String, Object>> updates = new LinkedHashMap<>();
    for (String emailId : emailIds) {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("keywords/" + keyword, enabled ? true : null);
      updates.put(emailId, patch);
    }

    JmapRequest request =
        new JmapRequest(
            List.of("urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"),
            List.of(
                new JmapMethodCall(
                    "Email/set", Map.of("accountId", accountId, "update", updates), "0")));

    EmailSetResult result =
        convertResponse(invoke(session, request, "Email/set"), EmailSetResult.class);
    return validateEmailUpdateResult(accountId, emailIds, result);
  }

  private EmailUpdateResult validateEmailUpdateResult(
      String accountId, List<String> emailIds, EmailSetResult result) {

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

    Set<String> requestedIds = Set.copyOf(emailIds);
    Set<String> returnedIds = new HashSet<>(updated.keySet());
    if (updated.keySet().stream().anyMatch(notUpdated::containsKey)
        || notUpdated.values().stream()
            .anyMatch(error -> error == null || error.type() == null || error.type().isBlank())) {
      throw mailServiceUnavailable();
    }
    returnedIds.addAll(notUpdated.keySet());
    if (!returnedIds.equals(requestedIds)) {
      throw mailServiceUnavailable();
    }

    return new EmailUpdateResult(
        emailIds.stream().filter(updated::containsKey).toList(),
        emailIds.stream()
            .filter(notUpdated::containsKey)
            .map(emailId -> new EmailUpdateResult.Failure(emailId, notUpdated.get(emailId).type()))
            .toList());
  }

  private EmailUpdateResult validateEmailDestroyResult(
      String accountId, List<String> emailIds, EmailSetResult result) {
    if (result == null
        || !accountId.equals(result.accountId())
        || result.oldState() == null
        || result.oldState().isBlank()
        || result.newState() == null
        || result.newState().isBlank()) {
      throw mailServiceUnavailable();
    }

    List<String> destroyed = result.destroyed() == null ? List.of() : result.destroyed();
    Map<String, EmailSetResult.SetError> notDestroyed =
        result.notDestroyed() == null ? Map.of() : result.notDestroyed();
    if (!hasUniqueIds(destroyed)
        || destroyed.stream().anyMatch(notDestroyed::containsKey)
        || notDestroyed.values().stream()
            .anyMatch(error -> error == null || error.type() == null || error.type().isBlank())) {
      throw mailServiceUnavailable();
    }

    Set<String> requestedIds = Set.copyOf(emailIds);
    Set<String> returnedIds = new HashSet<>(destroyed);
    returnedIds.addAll(notDestroyed.keySet());
    if (!returnedIds.equals(requestedIds)) {
      throw mailServiceUnavailable();
    }

    Set<String> destroyedIds = Set.copyOf(destroyed);
    return new EmailUpdateResult(
        emailIds.stream().filter(destroyedIds::contains).toList(),
        emailIds.stream()
            .filter(notDestroyed::containsKey)
            .map(
                emailId -> new EmailUpdateResult.Failure(emailId, notDestroyed.get(emailId).type()))
            .toList());
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

  private boolean isSameOrigin(URI candidate, URI expected) {
    return isValidApiUrl(candidate)
        && expected != null
        && candidate.getScheme().equalsIgnoreCase(expected.getScheme())
        && candidate.getHost().equalsIgnoreCase(expected.getHost())
        && effectivePort(candidate) == effectivePort(expected);
  }

  private int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private boolean hasUniqueIds(List<String> ids) {
    return ids != null
        && ids.stream().allMatch(id -> id != null && !id.isBlank())
        && new HashSet<>(ids).size() == ids.size();
  }

  private boolean isValidMailboxIds(Map<String, Boolean> mailboxIds) {
    return mailboxIds != null
        && !mailboxIds.isEmpty()
        && mailboxIds.entrySet().stream()
            .allMatch(
                entry ->
                    entry.getKey() != null
                        && !entry.getKey().isBlank()
                        && Boolean.TRUE.equals(entry.getValue()));
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
    StalwartCredentials credentials = credentialsProvider.getCredentials();
    headers.setBasicAuth(credentials.username(), credentials.password());
    String requestId = RequestIdContext.current();
    if (requestId != null) {
      headers.set(RequestIdContext.HEADER_NAME, requestId);
    }
  }
}
