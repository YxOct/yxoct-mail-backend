package com.yxoct.mail.client.stalwart;

import com.yxoct.mail.client.stalwart.dto.JmapMethodCall;
import com.yxoct.mail.client.stalwart.dto.JmapRequest;
import com.yxoct.mail.common.web.RequestIdContext;
import com.yxoct.mail.config.StalwartProperties;
import com.yxoct.mail.config.StalwartProvisioningProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

  public String ensureAccount(long localAccountId, String emailAddress, String password) {
    AddressParts address = AddressParts.parse(emailAddress);
    String domainId = findDomainId(address.domain());
    String marker = managementMarker(localAccountId);
    RemoteAccount existing = findAccount(address, domainId);
    if (existing != null) {
      if (!marker.equals(existing.description())) {
        throw new StalwartProvisioningException("REMOTE_ADDRESS_CONFLICT");
      }
      return existing.id();
    }
    return createAccount(address.localPart(), domainId, password, marker);
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
        matches.add(
            new RemoteAccount(
                requiredText(item, "id", "INVALID_ACCOUNT_RESPONSE"),
                nullableText(item, "description")));
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
      throw new StalwartProvisioningException("ACCOUNT_CREATE_REJECTED");
    }
    return requiredText(created, "id", "INVALID_ACCOUNT_RESPONSE");
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
              .uri("/api")
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

  static String managementMarker(long localAccountId) {
    return "Managed by yxoct-mail-backend; localAccountId=" + localAccountId;
  }

  private record RemoteAccount(String id, String description) {}

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
