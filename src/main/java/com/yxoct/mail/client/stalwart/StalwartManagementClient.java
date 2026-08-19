package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.common.web.RequestIdContext;
import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class StalwartManagementClient {

  private static final String CORE_CAPABILITY = "urn:ietf:params:jmap:core";
  private static final String MANAGEMENT_CAPABILITY = "urn:stalwart:jmap";
  private static final String CALL_ID = "0";
  private static final String CREATION_ID = "new-account";

  private final RestClient restClient;
  private final StalwartProvisioningProperties properties;

  public StalwartManagementClient(
      RestClient.Builder builder,
      StalwartProperties stalwartProperties,
      StalwartProvisioningProperties provisioningProperties) {
    this.restClient = builder.baseUrl(stalwartProperties.baseUrl().toString()).build();
    this.properties = provisioningProperties;
  }

  public String ensureAccount(String emailAddress, String password, String displayName) {
    AddressParts address = AddressParts.parse(emailAddress);
    String domainId = findDomainId(address.domain());
    RemoteAccount existing = findAccount(address, domainId);
    if (existing != null) {
      if (!credentialsMatch(address.email(), password)) {
        throw new StalwartProvisioningException("REMOTE_ADDRESS_CONFLICT");
      }
      return existing.id();
    }
    return createAccount(address.localPart(), domainId, password, displayName);
  }

  public void checkAvailability() {
    try {
      restClient
          .get()
          .uri("/api/account")
          .headers(this::setRequestHeaders)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      String failureCode =
          exception.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
                  || exception.getStatusCode().equals(HttpStatus.FORBIDDEN)
              ? "MANAGEMENT_AUTHENTICATION_FAILED"
              : "MANAGEMENT_REQUEST_FAILED";
      throw new StalwartProvisioningException(failureCode, exception);
    } catch (RestClientException exception) {
      throw new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED", exception);
    }
  }

  public void updateAccountDisplayName(String accountId, String displayName) {
    JsonNode result =
        invoke(
            "x:Account/set",
            Map.of("update", Map.of(accountId, Map.of("description", displayName))));
    JsonNode rejected = result.path("notUpdated").path(accountId);
    if (rejected.isObject()) {
      throw new StalwartProvisioningException(
          "ACCOUNT_UPDATE_REJECTED", setErrorDiagnostic(rejected));
    }
    if (!result.path("updated").has(accountId)) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
  }

  public void setAccountEnabled(String accountId, boolean enabled) {
    Map<String, Object> permissions =
        enabled
            ? Map.of("@type", "Inherit")
            : Map.of(
                "@type", "Replace",
                "enabledPermissions", Map.of(),
                "disabledPermissions", Map.of());
    JsonNode result =
        invoke(
            "x:Account/set",
            Map.of("update", Map.of(accountId, Map.of("permissions", permissions))));
    JsonNode rejected = result.path("notUpdated").path(accountId);
    if (rejected.isObject()) {
      throw new StalwartProvisioningException(
          "ACCOUNT_STATUS_UPDATE_REJECTED", setErrorDiagnostic(rejected));
    }
    if (!result.path("updated").has(accountId)) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
  }

  public Optional<StalwartAccountSnapshot> inspectAccount(String accountId) {
    JsonNode result = invoke("x:Account/get", Map.of("ids", List.of(accountId)));
    JsonNode list = result.path("list");
    if (!list.isArray()) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
    List<JsonNode> accounts = elements(list);
    if (accounts.isEmpty()) {
      JsonNode notFound = result.path("notFound");
      if (notFound.isArray()
          && notFound.size() == 1
          && accountId.equals(textValue(notFound.path(0)))) {
        return Optional.empty();
      }
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
    if (accounts.size() != 1 || !accountId.equals(nullableText(accounts.getFirst(), "id"))) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
    return Optional.of(
        new StalwartAccountSnapshot(accountId, isAccountEnabled(accounts.getFirst())));
  }

  public StalwartAccountMetadata inspectAccountMetadata(String accountId, String domain) {
    JsonNode account = getAccount(accountId);
    String domainId = findDomainId(domain);
    Set<String> aliases = new HashSet<>();
    for (JsonNode alias : indexedObject(account.path("aliases")).values()) {
      if (domainId.equals(nullableText(alias, "domainId"))) {
        String name = requiredText(alias, "name", "INVALID_ACCOUNT_RESPONSE");
        aliases.add((name + "@" + domain).toLowerCase());
      }
    }
    return new StalwartAccountMetadata(nullableText(account, "description"), Set.copyOf(aliases));
  }

  public boolean addAccountAlias(String accountId, String emailAddress) {
    return updateAccountAlias(accountId, AddressParts.parse(emailAddress), true);
  }

  public boolean removeAccountAlias(String accountId, String emailAddress) {
    return updateAccountAlias(accountId, AddressParts.parse(emailAddress), false);
  }

  private boolean updateAccountAlias(String accountId, AddressParts address, boolean add) {
    String domainId = findDomainId(address.domain());
    JsonNode account = getAccount(accountId);
    Map<String, JsonNode> aliases = indexedObject(account.path("aliases"));
    String matchingKey = null;
    for (Map.Entry<String, JsonNode> entry : aliases.entrySet()) {
      JsonNode alias = entry.getValue();
      if (address.localPart().equalsIgnoreCase(nullableText(alias, "name"))
          && domainId.equals(nullableText(alias, "domainId"))) {
        matchingKey = entry.getKey();
        break;
      }
    }
    if ((add && matchingKey != null) || (!add && matchingKey == null)) {
      return false;
    }
    Map<String, Object> patch = new LinkedHashMap<>();
    if (add) {
      patch.put(
          "aliases/" + nextIndex(aliases.keySet()),
          Map.of("name", address.localPart(), "domainId", domainId));
    } else {
      patch.put("aliases/" + matchingKey, null);
    }
    JsonNode result = invoke("x:Account/set", Map.of("update", Map.of(accountId, patch)));
    JsonNode rejected = result.path("notUpdated").path(accountId);
    if (rejected.isObject()) {
      throw new StalwartProvisioningException(
          "ACCOUNT_ALIAS_UPDATE_REJECTED", setErrorDiagnostic(rejected));
    }
    if (!result.path("updated").has(accountId)) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
    return true;
  }

  private JsonNode getAccount(String accountId) {
    JsonNode result = invoke("x:Account/get", Map.of("ids", List.of(accountId)));
    List<JsonNode> accounts = elements(result.path("list"));
    if (accounts.size() != 1 || !accountId.equals(nullableText(accounts.getFirst(), "id"))) {
      throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
    }
    return accounts.getFirst();
  }

  private boolean isAccountEnabled(JsonNode account) {
    JsonNode permissions = account.path("permissions");
    String type = nullableText(permissions, "@type");
    if ("Inherit".equals(type)) {
      return true;
    }
    if ("Replace".equals(type)
        && permissions.path("enabledPermissions").isObject()
        && permissions.path("disabledPermissions").isObject()) {
      return permissions.path("enabledPermissions").size() > 0;
    }
    throw new StalwartProvisioningException("INVALID_ACCOUNT_RESPONSE");
  }

  private String findDomainId(String domain) {
    List<String> ids = queryIds("x:Domain/query", Map.of("name", domain));
    JsonNode result = invoke("x:Domain/get", Map.of("ids", ids));
    List<JsonNode> matches =
        elements(result.path("list")).stream()
            .filter(item -> domain.equalsIgnoreCase(nullableText(item, "name")))
            .toList();
    if (matches.size() != 1) {
      throw new StalwartProvisioningException("DOMAIN_NOT_FOUND");
    }
    return requiredText(matches.getFirst(), "id", "INVALID_DOMAIN_RESPONSE");
  }

  private RemoteAccount findAccount(AddressParts address, String domainId) {
    List<String> ids =
        queryIds("x:Account/query", Map.of("name", address.localPart(), "domainId", domainId));
    if (ids.isEmpty()) {
      return null;
    }
    JsonNode result = invoke("x:Account/get", Map.of("ids", ids));
    List<RemoteAccount> matches = new ArrayList<>();
    for (JsonNode item : elements(result.path("list"))) {
      if (address.email().equalsIgnoreCase(nullableText(item, "emailAddress"))) {
        matches.add(new RemoteAccount(requiredText(item, "id", "INVALID_ACCOUNT_RESPONSE")));
      }
    }
    if (matches.size() > 1) {
      throw new StalwartProvisioningException("AMBIGUOUS_REMOTE_ACCOUNT");
    }
    return matches.isEmpty() ? null : matches.getFirst();
  }

  private String createAccount(
      String localPart, String domainId, String password, String description) {
    Map<String, Object> account = new LinkedHashMap<>();
    account.put("@type", "User");
    account.put("name", localPart);
    account.put("domainId", domainId);
    account.put("description", description);
    account.put("credentials", Map.of("0", Map.of("@type", "Password", "secret", password)));
    account.put("memberGroupIds", Map.of());
    account.put("roles", Map.of("@type", "User"));
    account.put("permissions", Map.of("@type", "Inherit"));
    account.put("quotas", Map.of());
    account.put("aliases", Map.of());
    account.put("encryptionAtRest", Map.of("@type", "Disabled"));

    JsonNode result = invoke("x:Account/set", Map.of("create", Map.of(CREATION_ID, account)));
    JsonNode created = result.path("created").path(CREATION_ID);
    if (!created.isObject()) {
      throw new StalwartProvisioningException(
          "ACCOUNT_CREATE_REJECTED",
          setErrorDiagnostic(result.path("notCreated").path(CREATION_ID)));
    }
    return requiredText(created, "id", "INVALID_ACCOUNT_RESPONSE");
  }

  private boolean credentialsMatch(String emailAddress, String password) {
    try {
      restClient
          .get()
          .uri("/.well-known/jmap")
          .headers(headers -> setCredentialHeaders(headers, emailAddress, password))
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().equals(HttpStatus.UNAUTHORIZED)
          || exception.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
        return false;
      }
      throw new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED", exception);
    } catch (RestClientException exception) {
      throw new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED", exception);
    }
  }

  private List<String> queryIds(String method, Map<String, Object> filter) {
    JsonNode result = invoke(method, Map.of("filter", filter, "limit", 100));
    JsonNode ids = result.path("ids");
    if (!ids.isArray()) {
      throw new StalwartProvisioningException("INVALID_QUERY_RESPONSE");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode id : ids) {
      if (!id.isString() || id.stringValue().isBlank()) {
        throw new StalwartProvisioningException("INVALID_QUERY_RESPONSE");
      }
      values.add(id.stringValue());
    }
    if (values.stream().distinct().count() != values.size()) {
      throw new StalwartProvisioningException("INVALID_QUERY_RESPONSE");
    }
    return values;
  }

  private JsonNode invoke(String method, Map<String, Object> arguments) {
    JmapRequest request =
        new JmapRequest(
            List.of(CORE_CAPABILITY, MANAGEMENT_CAPABILITY),
            List.of(new JmapMethodCall(method, arguments, CALL_ID)));
    try {
      JsonNode response =
          restClient
              .post()
              .uri("/jmap")
              .headers(this::setRequestHeaders)
              .body(request)
              .retrieve()
              .body(JsonNode.class);
      JsonNode tuple = response == null ? null : response.path("methodResponses").path(0);
      if (tuple == null
          || !tuple.isArray()
          || tuple.size() != 3
          || !method.equals(textValue(tuple.path(0)))
          || !CALL_ID.equals(textValue(tuple.path(2)))
          || !tuple.path(1).isObject()) {
        throw new StalwartProvisioningException("INVALID_METHOD_RESPONSE");
      }
      return tuple.path(1);
    } catch (RestClientException exception) {
      throw new StalwartProvisioningException("MANAGEMENT_REQUEST_FAILED", exception);
    }
  }

  private void setRequestHeaders(HttpHeaders headers) {
    headers.setBearerAuth(properties.managementApiKey());
    setRequestId(headers);
  }

  private void setCredentialHeaders(HttpHeaders headers, String emailAddress, String password) {
    headers.setBasicAuth(emailAddress, password);
    setRequestId(headers);
  }

  private void setRequestId(HttpHeaders headers) {
    String requestId = RequestIdContext.current();
    if (requestId != null) {
      headers.set(RequestIdContext.HEADER_NAME, requestId);
    }
  }

  private List<JsonNode> elements(JsonNode node) {
    if (!node.isArray()) {
      throw new StalwartProvisioningException("INVALID_GET_RESPONSE");
    }
    List<JsonNode> values = new ArrayList<>();
    node.forEach(values::add);
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new StalwartProvisioningException("INVALID_GET_RESPONSE");
    }
    return values;
  }

  private Map<String, JsonNode> indexedObject(JsonNode node) {
    if (!node.isObject()) {
      throw new StalwartProvisioningException("INVALID_GET_RESPONSE");
    }
    Map<String, JsonNode> values = new LinkedHashMap<>();
    node.properties()
        .forEach(
            entry -> {
              if (!entry.getKey().matches("0|[1-9][0-9]*") || !entry.getValue().isObject()) {
                throw new StalwartProvisioningException("INVALID_GET_RESPONSE");
              }
              values.put(entry.getKey(), entry.getValue());
            });
    return values;
  }

  private int nextIndex(Set<String> keys) {
    Set<Integer> indexes = new HashSet<>();
    try {
      for (String key : keys) {
        indexes.add(Integer.parseInt(key));
      }
    } catch (NumberFormatException exception) {
      throw new StalwartProvisioningException("INVALID_GET_RESPONSE", exception);
    }
    int index = 0;
    while (indexes.contains(index)) {
      index++;
    }
    return index;
  }

  private String requiredText(JsonNode node, String field, String failureCode) {
    String value = nullableText(node, field);
    if (value == null || value.isBlank()) {
      throw new StalwartProvisioningException(failureCode);
    }
    return value;
  }

  private String nullableText(JsonNode node, String field) {
    return textValue(node.path(field));
  }

  private String textValue(JsonNode node) {
    return node.isString() ? node.stringValue() : null;
  }

  private String setErrorDiagnostic(JsonNode error) {
    if (!error.isObject()) {
      return "type=missing";
    }
    String type = nullableText(error, "type");
    List<String> properties = new ArrayList<>();
    JsonNode propertyNodes = error.path("properties");
    if (propertyNodes.isArray()) {
      for (JsonNode property : propertyNodes) {
        String value = textValue(property);
        if (value != null && !value.isBlank()) {
          properties.add(value);
        }
      }
    }
    String diagnostic = "type=" + (type == null ? "unknown" : type);
    return properties.isEmpty() ? diagnostic : diagnostic + ", properties=" + properties;
  }

  private record RemoteAccount(String id) {}

  private record AddressParts(String email, String localPart, String domain) {

    static AddressParts parse(String emailAddress) {
      int separator = emailAddress == null ? -1 : emailAddress.lastIndexOf('@');
      if (separator <= 0 || separator == emailAddress.length() - 1) {
        throw new StalwartProvisioningException("INVALID_LOCAL_ADDRESS");
      }
      return new AddressParts(
          emailAddress,
          emailAddress.substring(0, separator),
          emailAddress.substring(separator + 1));
    }
  }
}
