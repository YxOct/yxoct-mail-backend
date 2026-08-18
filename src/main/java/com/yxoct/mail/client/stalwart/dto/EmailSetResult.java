package com.yxoct.mail.client.stalwart.dto;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record EmailSetResult(
    String accountId,
    String oldState,
    String newState,
    Map<String, JsonNode> updated,
    Map<String, SetError> notUpdated,
    List<String> destroyed,
    Map<String, SetError> notDestroyed) {

  public record SetError(String type) {}
}
