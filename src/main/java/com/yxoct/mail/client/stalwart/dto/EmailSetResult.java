package com.yxoct.mail.client.stalwart.dto;

import java.util.Map;
import tools.jackson.databind.JsonNode;

public record EmailSetResult(
    String accountId,
    String oldState,
    String newState,
    Map<String, JsonNode> updated,
    Map<String, SetError> notUpdated) {

  public record SetError(String type) {}
}
