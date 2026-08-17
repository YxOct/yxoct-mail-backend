package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yxoct.mail.client.stalwart.dto.EmailQueryResult;
import com.yxoct.mail.client.stalwart.dto.JmapSession;
import com.yxoct.mail.common.exception.BusinessException;
import com.yxoct.mail.common.exception.ErrorCode;
import com.yxoct.mail.config.StalwartProperties;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
            new StalwartProperties(URI.create("http://localhost"), "user", "password"),
            JsonMapper.builder().build());
  }

  @AfterEach
  void verifyRequests() {
    server.verify();
  }

  @Test
  void rejectsSessionWithoutMailAccount() {
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andRespond(
            withSuccess(
                "{\"primaryAccounts\":{},\"apiUrl\":\"http://localhost/jmap\"}",
                MediaType.APPLICATION_JSON));

    assertMailServiceUnavailable(client::getSession);
  }

  @Test
  void rejectsInvalidSessionBeforeSendingRequest() {
    assertMailServiceUnavailable(() -> client.queryEmails(null, "inbox", 0, 20));
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
        .andRespond(
            withSuccess(
                "{\"methodResponses\":[[\"Email/query\",{\"position\":0,\"total\":1,\"ids\":[\"email-1\"]},\"0\"]]}",
                MediaType.APPLICATION_JSON));

    EmailQueryResult result = client.queryEmails(session(), "inbox", 0, 20);

    assertThat(result.ids()).containsExactly("email-1");
    assertThat(result.total()).isEqualTo(1);
  }

  private void assertMailServiceUnavailable(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.MAIL_SERVICE_UNAVAILABLE);
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
