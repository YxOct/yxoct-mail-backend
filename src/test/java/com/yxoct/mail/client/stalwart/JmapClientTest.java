package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.yxoct.mail.client.stalwart.dto.EmailAttachmentResult;
import com.yxoct.mail.client.stalwart.dto.EmailDetailResult;
import com.yxoct.mail.client.stalwart.dto.EmailListResult;
import com.yxoct.mail.client.stalwart.dto.EmailMailboxResult;
import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.EmailUpdateResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.common.web.RequestIdContext;
import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.domain.mail.MailQueryFilter;
import com.yxoct.mail.domain.mail.MailSort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class JmapClientTest {

  private MockRestServiceServer server;
  private JmapClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new JmapClient(
            builder,
            new StalwartProperties(
                URI.create("http://localhost"), "user", "password", Duration.ofMinutes(1)),
            JsonMapper.builder().build(),
            new StalwartClientMetrics(new SimpleMeterRegistry()));
  }

  @AfterEach
  void verifyRequests() {
    try {
      server.verify();
    } finally {
      MDC.clear();
    }
  }

  @Test
  void propagatesRequestIdToStalwart() {
    MDC.put(RequestIdContext.MDC_KEY, "request-123");
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andExpect(header(RequestIdContext.HEADER_NAME, "request-123"))
        .andRespond(
            withSuccess(
                "{\"primaryAccounts\":{\"urn:ietf:params:jmap:mail\":\"account-1\"},\"apiUrl\":\"http://localhost/jmap\",\"state\":\"state\"}",
                MediaType.APPLICATION_JSON));

    assertThat(client.getSession()).isNotNull();
  }

  @Test
  void rejectsSessionWithoutMailAccount() {
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andRespond(
            withSuccess(
                "{\"primaryAccounts\":{},\"apiUrl\":\"http://localhost/jmap\",\"state\":\"state\"}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(client::getSession);
  }

  @Test
  void rejectsInvalidSessionBeforeSendingRequest() {
    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                null, "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void rejectsMalformedMethodResponseTuple() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{}]]}", MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void mapsHttpErrorToMailServiceUnavailable() {
    server.expect(requestTo("http://localhost/.well-known/jmap")).andRespond(withServerError());

    assertMailServiceUnavailable(client::getSession);
  }

  @Test
  void mapsConnectionFailureToMailServiceUnavailable() {
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andRespond(withException(new IOException("Connection timed out")));

    assertMailServiceUnavailable(client::getSession);
  }

  @Test
  void mapsTimeoutToGatewayTimeout() {
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertBusinessError(client::getSession, ErrorCode.MAIL_SERVICE_TIMEOUT);
  }

  @Test
  void mapsRejectedCredentialsToAuthenticationFailure() {
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andRespond(withUnauthorizedRequest());

    assertBusinessError(client::getSession, ErrorCode.MAIL_SERVICE_AUTHENTICATION_FAILED);
  }

  @Test
  void rejectsJmapMethodError() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"error\",{\"type\":\"invalidArguments\"},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void rejectsEmptyMethodResponse() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(withSuccess("{\"methodResponses\":[]}", MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void rejectsResponseWithUnexpectedCallId() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"position\":0,\"ids\":[]},\"1\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void returnsValidatedQueryResponse() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.inMailbox").value("inbox"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].property").value("receivedAt"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].isAscending").value(false))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"accountId\":\"account-1\",\"queryState\":\"query-state\",\"position\":0,\"total\":1,\"ids\":[\"email-1\"]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailQueryResult result =
        client.queryEmails(
            session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort());

    assertThat(result.ids()).containsExactly("email-1");
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  void combinesTextReadAndStarredQueryFilters() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.operator").value("AND"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.conditions[0].inMailbox").value("inbox"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.conditions[1].text").value("invoice"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.conditions[2].notKeyword").value("$seen"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].filter.conditions[3].hasKeyword").value("$flagged"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].property").value("subject"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].isAscending").value(true))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"accountId\":\"account-1\",\"queryState\":\"query-state\",\"position\":0,\"total\":0,\"ids\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailQueryResult result =
        client.queryEmails(
            session(),
            "inbox",
            0,
            20,
            new MailQueryFilter(" invoice ", false, true),
            MailSort.parse("subject", "asc"));

    assertThat(result.ids()).isEmpty();
  }

  @Test
  void mapsInvalidResponsePayloadToMailServiceUnavailable() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",\"invalid\",\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void rejectsQueryResponseForAnotherAccount() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"accountId\":\"other-account\",\"queryState\":\"query-state\",\"position\":0,\"total\":0,\"ids\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () ->
            client.queryEmails(
                session(), "inbox", 0, 20, MailQueryFilter.none(), MailSort.defaultSort()));
  }

  @Test
  void deserializesTypedEmailDetailFields() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].properties[9]").value("attachments"))
        .andExpect(jsonPath("$.methodCalls[0][1].bodyProperties[1]").value("blobId"))
        .andRespond(
            withSuccess(
                """
                {
                  "methodResponses": [[
                    "Email/get",
                    {
                      "accountId": "account-1",
                      "state": "state",
                      "list": [{
                        "id": "email-1",
                        "keywords": {"$seen": true, "$flagged": true},
                        "from": [{"name": "Sender", "email": "sender@example.com"}],
                        "bodyValues": {
                          "part-1": {"value": "Hello", "isTruncated": false}
                        },
                        "textBody": [{
                          "partId": "part-1",
                          "type": "text/plain",
                          "size": 5
                        }],
                        "attachments": [{
                          "partId": "part-2",
                          "blobId": "blob-1",
                          "name": "report.pdf",
                          "type": "application/pdf",
                          "size": 2048,
                          "disposition": "attachment"
                        }]
                      }],
                      "notFound": []
                    },
                    "0"
                  ]]
                }
                """,
                MediaType.APPLICATION_JSON));

    EmailDetailResult result = client.getEmailDetails(session(), List.of("email-1"));

    EmailDetailResult.EmailInfo email = result.list().getFirst();
    assertThat(email.from().getFirst().email()).isEqualTo("sender@example.com");
    assertThat(email.textBody().getFirst().partId()).isEqualTo("part-1");
    assertThat(email.bodyValues().get("part-1").value()).isEqualTo("Hello");
    assertThat(email.attachments().getFirst().blobId()).isEqualTo("blob-1");
    assertThat(email.attachments().getFirst().name()).isEqualTo("report.pdf");
    assertThat(email.keywords()).containsEntry("$seen", true);
    assertThat(email.keywords()).containsEntry("$flagged", true);
  }

  @Test
  void requestsAndDeserializesEmailSummaryKeywords() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].properties[4]").value("keywords"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/get\",{\"accountId\":\"account-1\",\"state\":\"state\",\"list\":[{\"id\":\"email-1\",\"keywords\":{\"$seen\":true,\"$flagged\":true}}],\"notFound\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailListResult result = client.getEmailSummaries(session(), List.of("email-1"));

    assertThat(result.list().getFirst().keywords())
        .containsEntry("$seen", true)
        .containsEntry("$flagged", true);
  }

  @Test
  void streamsBlobFromExpandedDownloadUrl() {
    server
        .expect(
            requestTo(
                "http://localhost/download/account-1/blob-1/report%20Q1.pdf?type=application/pdf"))
        .andExpect(header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
        .andRespond(withSuccess("attachment-data", MediaType.APPLICATION_OCTET_STREAM));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    client.downloadBlob(
        sessionWithDownloadUrl("http://localhost/download/{accountId}/{blobId}/{name}?type={type}"),
        "blob-1",
        "report Q1.pdf",
        "application/pdf",
        output);

    assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo("attachment-data");
  }

  @Test
  void fetchesOnlyAttachmentMetadataForDownloadValidation() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].properties[0]").value("id"))
        .andExpect(jsonPath("$.methodCalls[0][1].properties[1]").value("attachments"))
        .andExpect(jsonPath("$.methodCalls[0][1].fetchTextBodyValues").doesNotExist())
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/get\",{\"accountId\":\"account-1\",\"state\":\"state\",\"list\":[{\"id\":\"email-1\",\"attachments\":[{\"partId\":\"part-1\",\"blobId\":\"blob-1\",\"size\":2048,\"name\":\"report.pdf\",\"type\":\"application/pdf\"}]}],\"notFound\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailAttachmentResult result = client.getEmailAttachments(session(), List.of("email-1"));

    assertThat(result.list().getFirst().attachments().getFirst().blobId()).isEqualTo("blob-1");
  }

  @Test
  void rejectsCrossOriginDownloadUrl() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    assertMailServiceUnavailable(
        () ->
            client.downloadBlob(
                sessionWithDownloadUrl(
                    "https://untrusted.example/download/{accountId}/{blobId}/{name}?type={type}"),
                "blob-1",
                "report.pdf",
                "application/pdf",
                output));
  }

  @Test
  void mapsMissingDownloadedBlobToAttachmentNotFound() {
    server
        .expect(
            requestTo("http://localhost/download/account-1/blob-1/attachment?type=application/pdf"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertBusinessError(
        () ->
            client.downloadBlob(
                sessionWithDownloadUrl(
                    "http://localhost/download/{accountId}/{blobId}/{name}?type={type}"),
                "blob-1",
                null,
                "application/pdf",
                new ByteArrayOutputStream()),
        ErrorCode.ATTACHMENT_NOT_FOUND);
  }

  @Test
  void marksEmailAsRead() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("Email/set"))
        .andExpect(jsonPath("$.methodCalls[0][1].update['email-1']['keywords/$seen']").value(true))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailUpdateResult result = client.setEmailsRead(session(), List.of("email-1"), true);

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failures()).isEmpty();
  }

  @Test
  void marksEmailAsUnreadByRemovingSeenKeyword() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "methodCalls": [[
                        "Email/set",
                        {"update": {"email-1": {"keywords/$seen": null}}},
                        "0"
                      ]]
                    }
                    """))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    client.setEmailsRead(session(), List.of("email-1"), false);
  }

  @Test
  void returnsPartialBatchUpdateResult() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].update['email-1']['keywords/$seen']").value(true))
        .andExpect(jsonPath("$.methodCalls[0][1].update['missing']['keywords/$seen']").value(true))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null},\"notUpdated\":{\"missing\":{\"type\":\"notFound\"}}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailUpdateResult result = client.setEmailsRead(session(), List.of("email-1", "missing"), true);

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failures())
        .containsExactly(new EmailUpdateResult.Failure("missing", "notFound"));
  }

  @Test
  void rejectsBatchResponseThatOmitsRequestedId() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () -> client.setEmailsRead(session(), List.of("email-1", "email-2"), true));
  }

  @Test
  void starsEmail() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].update['email-1']['keywords/$flagged']").value(true))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    client.setEmailsStarred(session(), List.of("email-1"), true);
  }

  @Test
  void unstarsEmailByRemovingFlaggedKeyword() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "methodCalls": [[
                        "Email/set",
                        {"update": {"email-1": {"keywords/$flagged": null}}},
                        "0"
                      ]]
                    }
                    """))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    client.setEmailsStarred(session(), List.of("email-1"), false);
  }

  @Test
  void getsEmailMailboxMemberships() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].properties[1]").value("mailboxIds"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/get\",{\"accountId\":\"account-1\",\"state\":\"state\",\"list\":[{\"id\":\"email-1\",\"mailboxIds\":{\"inbox\":true,\"archive\":true}}],\"notFound\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailMailboxResult result = client.getEmailMailboxes(session(), List.of("email-1"));

    assertThat(result.list().getFirst().mailboxIds())
        .containsOnlyKeys("inbox", "archive")
        .containsValue(true);
  }

  @Test
  void rejectsInvalidEmailMailboxMemberships() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/get\",{\"accountId\":\"account-1\",\"state\":\"state\",\"list\":[{\"id\":\"email-1\",\"mailboxIds\":{}}],\"notFound\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.getEmailMailboxes(session(), List.of("email-1")));
  }

  @Test
  void replacesEmailMailboxMemberships() {
    Map<String, List<String>> updates = new LinkedHashMap<>();
    updates.put("email-1", List.of("trash"));
    updates.put("email-2", List.of("inbox", "archive"));
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("Email/set"))
        .andExpect(jsonPath("$.methodCalls[0][1].update['email-1'].mailboxIds.trash").value(true))
        .andExpect(jsonPath("$.methodCalls[0][1].update['email-2'].mailboxIds.inbox").value(true))
        .andExpect(jsonPath("$.methodCalls[0][1].update['email-2'].mailboxIds.archive").value(true))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"updated\":{\"email-1\":null,\"email-2\":null}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailUpdateResult result = client.setEmailMailboxes(session(), updates);

    assertThat(result.updatedIds()).containsExactly("email-1", "email-2");
  }

  @Test
  void destroysEmailsAndReturnsPartialFailures() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("Email/set"))
        .andExpect(jsonPath("$.methodCalls[0][1].destroy[0]").value("email-1"))
        .andExpect(jsonPath("$.methodCalls[0][1].destroy[1]").value("email-2"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"destroyed\":[\"email-1\"],\"notDestroyed\":{\"email-2\":{\"type\":\"forbidden\"}}},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailUpdateResult result = client.destroyEmails(session(), List.of("email-1", "email-2"));

    assertThat(result.updatedIds()).containsExactly("email-1");
    assertThat(result.failures())
        .containsExactly(new EmailUpdateResult.Failure("email-2", "forbidden"));
  }

  @Test
  void rejectsIncompleteDestroyResponse() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/set\",{\"accountId\":\"account-1\",\"oldState\":\"old\",\"newState\":\"new\",\"destroyed\":[\"email-1\"]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(
        () -> client.destroyEmails(session(), List.of("email-1", "email-2")));
  }

  private void assertMailServiceUnavailable(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertBusinessError(call, ErrorCode.MAIL_SERVICE_UNAVAILABLE);
  }

  private void assertBusinessError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(errorCode);
  }

  private JmapSession session() {
    return new JmapSession(
        Map.of(),
        Map.of("urn:ietf:params:jmap:mail", "account-1"),
        "user",
        URI.create("http://localhost/jmap"),
        null,
        null,
        null,
        "state");
  }

  private JmapSession sessionWithDownloadUrl(String downloadUrl) {
    JmapSession session = session();
    return new JmapSession(
        session.accounts(),
        session.primaryAccounts(),
        session.username(),
        session.apiUrl(),
        downloadUrl,
        session.uploadUrl(),
        session.eventSourceUrl(),
        session.state());
  }
}
