package com.yxoct.mail.client.stalwart.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public record JmapMethodCall(String methodName, Object arguments, String callId) {

  @JsonValue
  public Object[] toJson() {
    return new Object[] {methodName, arguments, callId};
  }
}
