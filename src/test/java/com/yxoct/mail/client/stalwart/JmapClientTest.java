package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    assertMailServiceUnavailable(() -> client.queryEmails(null, "inbox", 0, 20));
  }

  @Test
  void rejectsMalformedMethodResponseTuple() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{}]]}", MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
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

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
  }

  @Test
  void rejectsEmptyMethodResponse() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(withSuccess("{\"methodResponses\":[]}", MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
  }

  @Test
  void rejectsResponseWithUnexpectedCallId() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"position\":0,\"ids\":[]},\"1\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
  }

  @Test
  void returnsValidatedQueryResponse() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].property").value("receivedAt"))
        .andExpect(jsonPath("$.methodCalls[0][1].sort[0].isAscending").value(false))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"accountId\":\"account-1\",\"queryState\":\"query-state\",\"position\":0,\"total\":1,\"ids\":[\"email-1\"]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailQueryResult result = client.queryEmails(session(), "inbox", 0, 20);

    assertThat(result.ids()).containsExactly("email-1");
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  void mapsInvalidResponsePayloadToMailServiceUnavailable() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",\"invalid\",\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
  }

  @Test
  void rejectsQueryResponseForAnotherAccount() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"accountId\":\"other-account\",\"queryState\":\"query-state\",\"position\":0,\"total\":0,\"ids\":[]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(() -> client.queryEmails(session(), "inbox", 0, 20));
  }

  @Test
  void deserializesTypedEmailDetailFields() {
    server
        .expect(requestTo("http://localhost/jmap"))
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
}
