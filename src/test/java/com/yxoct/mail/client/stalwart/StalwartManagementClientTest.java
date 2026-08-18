package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class StalwartManagementClientTest {

  private MockRestServiceServer server;
  private StalwartManagementClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new StalwartManagementClient(
            builder,
            new StalwartProperties(
                URI.create("http://localhost"), "test", "test", Duration.ofMinutes(1)),
            provisioningProperties());
  }

  @AfterEach
  void verifyRequests() {
    server.verify();
  }

  @Test
  void createsAccountWithManagedMarkerAndInternalCredential() {
    expectDomainLookup();
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/query"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.name").value("alice"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.domainId").value("domain-1"))
        .andRespond(methodResponse("x:Account/query", "{\"ids\":[]}"));
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(header("Authorization", "Bearer API-key"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/set"))
        .andExpect(jsonPath("$.methodCalls[0][1].create.new-account.name").value("alice"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].create.new-account.description")
                .value(StalwartManagementClient.managementMarker(42)))
        .andExpect(
            jsonPath("$.methodCalls[0][1].create.new-account.credentials['0'].secret")
                .value("internal-secret"))
        .andRespond(
            methodResponse(
                "x:Account/set", "{\"created\":{\"new-account\":{\"id\":\"account-1\"}}}"));

    assertThat(client.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .isEqualTo("account-1");
  }

  @Test
  void reusesOnlyAnAccountCreatedForTheSameLocalAccount() {
    expectDomainLookup();
    expectExistingAccount(StalwartManagementClient.managementMarker(42));

    assertThat(client.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .isEqualTo("account-1");
  }

  @Test
  void rejectsAnUnmanagedAddressConflict() {
    expectDomainLookup();
    expectExistingAccount("Created outside this application");

    assertThatThrownBy(() -> client.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception -> assertThat(exception.failureCode()).isEqualTo("REMOTE_ADDRESS_CONFLICT"));
  }

  @Test
  void exposesOnlySetErrorTypeAndPropertyNamesWhenCreationIsRejected() {
    expectDomainLookup();
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(methodResponse("x:Account/query", "{\"ids\":[]}"));
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            methodResponse(
                "x:Account/set",
                "{\"notCreated\":{\"new-account\":{\"type\":\"invalidProperties\","
                    + "\"description\":\"must not be logged\","
                    + "\"properties\":[\"credentials\"]}}}"));

    assertThatThrownBy(() -> client.ensureAccount(42, "alice@yxoct.com", "internal-secret"))
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception -> {
              assertThat(exception.failureCode()).isEqualTo("ACCOUNT_CREATE_REJECTED");
              assertThat(exception.diagnostic())
                  .isEqualTo("type=invalidProperties, properties=[credentials]")
                  .doesNotContain("must not be logged", "internal-secret");
            });
  }

  private void expectDomainLookup() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Domain/query"))
        .andExpect(jsonPath("$.methodCalls[0][1].filter.name").value("yxoct.com"))
        .andRespond(methodResponse("x:Domain/query", "{\"ids\":[\"domain-1\"]}"));
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Domain/get"))
        .andRespond(
            methodResponse(
                "x:Domain/get", "{\"list\":[{\"id\":\"domain-1\",\"name\":\"yxoct.com\"}]}"));
  }

  private void expectExistingAccount(String description) {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/query"))
        .andRespond(methodResponse("x:Account/query", "{\"ids\":[\"account-1\"]}"));
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/get"))
        .andRespond(
            methodResponse(
                "x:Account/get",
                "{\"list\":[{\"id\":\"account-1\",\"emailAddress\":\"alice@yxoct.com\",\"description\":\""
                    + description
                    + "\"}]}"));
  }

  private ResponseCreator methodResponse(String method, String result) {
    return withSuccess(
        "{\"methodResponses\":[[\"" + method + "\"," + result + ",\"0\"]]}",
        MediaType.APPLICATION_JSON);
  }

  private StalwartProvisioningProperties provisioningProperties() {
    return new StalwartProvisioningProperties(
        true,
        "API-key",
        Base64.getEncoder().encodeToString(new byte[32]),
        Duration.ofSeconds(10),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        Duration.ofHours(1),
        20);
  }
}
