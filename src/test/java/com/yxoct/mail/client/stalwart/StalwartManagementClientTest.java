package com.yxoct.mail.client.stalwart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
            new StalwartProperties(URI.create("http://localhost"), Duration.ofMinutes(1)),
            provisioningProperties());
  }

  @AfterEach
  void verifyRequests() {
    server.verify();
  }

  @Test
  void createsAccountWithDisplayNameAndInternalCredential() {
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
            jsonPath("$.methodCalls[0][1].create.new-account.description").value("Alice Zhang"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].create.new-account.credentials['0'].secret")
                .value("internal-secret"))
        .andRespond(
            methodResponse(
                "x:Account/set", "{\"created\":{\"new-account\":{\"id\":\"account-1\"}}}"));

    assertThat(client.ensureAccount("alice@yxoct.com", "internal-secret", "Alice Zhang"))
        .isEqualTo("account-1");
  }

  @Test
  void reusesAnExistingAccountWhenItsCredentialMatches() {
    expectDomainLookup();
    expectExistingAccount();
    expectCredentialVerification(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThat(client.ensureAccount("alice@yxoct.com", "internal-secret", "Alice"))
        .isEqualTo("account-1");
  }

  @Test
  void rejectsAnExistingAccountWhenItsCredentialDoesNotMatch() {
    expectDomainLookup();
    expectExistingAccount();
    expectCredentialVerification(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.ensureAccount("alice@yxoct.com", "internal-secret", "Alice"))
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception -> assertThat(exception.failureCode()).isEqualTo("REMOTE_ADDRESS_CONFLICT"));
  }

  @Test
  void treatsCredentialVerificationServerErrorsAsRetryableManagementFailures() {
    expectDomainLookup();
    expectExistingAccount();
    expectCredentialVerification(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.ensureAccount("alice@yxoct.com", "internal-secret", "Alice"))
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception ->
                assertThat(exception.failureCode()).isEqualTo("MANAGEMENT_REQUEST_FAILED"));
  }

  @Test
  void checksManagementApiAvailabilityWithTheApiKey() {
    server
        .expect(requestTo("http://localhost/api/account"))
        .andExpect(header("Authorization", "Bearer API-key"))
        .andRespond(withSuccess());

    client.checkAvailability();
  }

  @Test
  void classifiesRejectedManagementApiCredentials() {
    server
        .expect(requestTo("http://localhost/api/account"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(client::checkAvailability)
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception ->
                assertThat(exception.failureCode()).isEqualTo("MANAGEMENT_AUTHENTICATION_FAILED"));
  }

  @Test
  void updatesAnAccountDisplayName() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(header("Authorization", "Bearer API-key"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/set"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].update.account-1.description").value("Alice Zhang"))
        .andRespond(methodResponse("x:Account/set", "{\"updated\":{\"account-1\":null}}"));

    client.updateAccountDisplayName("account-1", "Alice Zhang");
  }

  @Test
  void reportsARejectedDisplayNameUpdateWithoutLeakingTheDescription() {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andRespond(
            methodResponse(
                "x:Account/set",
                "{\"notUpdated\":{\"account-1\":{\"type\":\"forbidden\","
                    + "\"description\":\"must not be logged\"}}}"));

    assertThatThrownBy(() -> client.updateAccountDisplayName("account-1", "Alice Zhang"))
        .isInstanceOfSatisfying(
            StalwartProvisioningException.class,
            exception -> {
              assertThat(exception.failureCode()).isEqualTo("ACCOUNT_UPDATE_REJECTED");
              assertThat(exception.diagnostic())
                  .isEqualTo("type=forbidden")
                  .doesNotContain("must not be logged", "Alice Zhang");
            });
  }

  @Test
  void addsAnAccountAlias() {
    expectDomainLookup();
    expectAccountAliases("{}");
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/set"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].update.account-1['aliases/0'].name").value("hello"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].update.account-1['aliases/0'].domainId")
                .value("domain-1"))
        .andRespond(methodResponse("x:Account/set", "{\"updated\":{\"account-1\":null}}"));

    assertThat(client.addAccountAlias("account-1", "hello@yxoct.com")).isTrue();
  }

  @Test
  void removesAnAccountAlias() {
    expectDomainLookup();
    expectAccountAliases("{\"0\":{\"name\":\"hello\",\"domainId\":\"domain-1\"}}");
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/set"))
        .andExpect(jsonPath("$.methodCalls[0][1].update.account-1['aliases/0']").doesNotExist())
        .andRespond(methodResponse("x:Account/set", "{\"updated\":{\"account-1\":null}}"));

    client.removeAccountAlias("account-1", "hello@yxoct.com");
  }

  @Test
  void treatsAnExistingAliasAsAnIdempotentSuccess() {
    expectDomainLookup();
    expectAccountAliases("{\"0\":{\"name\":\"hello\",\"domainId\":\"domain-1\"}}");

    assertThat(client.addAccountAlias("account-1", "hello@yxoct.com")).isFalse();
  }

  @Test
  void fillsTheFirstAvailableAliasIndex() {
    expectDomainLookup();
    expectAccountAliases(
        "{\"0\":{\"name\":\"first\",\"domainId\":\"domain-1\"},"
            + "\"2\":{\"name\":\"third\",\"domainId\":\"domain-1\"}}");
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(
            jsonPath("$.methodCalls[0][1].update.account-1['aliases/1'].name").value("hello"))
        .andRespond(methodResponse("x:Account/set", "{\"updated\":{\"account-1\":null}}"));

    assertThat(client.addAccountAlias("account-1", "hello@yxoct.com")).isTrue();
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

    assertThatThrownBy(() -> client.ensureAccount("alice@yxoct.com", "internal-secret", "Alice"))
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

  private void expectExistingAccount() {
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
                "{\"list\":[{\"id\":\"account-1\",\"emailAddress\":\"alice@yxoct.com\"}]}"));
  }

  private void expectAccountAliases(String aliases) {
    server
        .expect(requestTo("http://localhost/jmap"))
        .andExpect(jsonPath("$.methodCalls[0][0]").value("x:Account/get"))
        .andRespond(
            methodResponse(
                "x:Account/get",
                "{\"list\":[{\"id\":\"account-1\",\"aliases\":" + aliases + "}]}"));
  }

  private void expectCredentialVerification(ResponseCreator response) {
    String credentials =
        Base64.getEncoder()
            .encodeToString(
                "alice@yxoct.com:internal-secret".getBytes(StandardCharsets.ISO_8859_1));
    server
        .expect(requestTo("http://localhost/.well-known/jmap"))
        .andExpect(header("Authorization", "Basic " + credentials))
        .andRespond(response);
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
