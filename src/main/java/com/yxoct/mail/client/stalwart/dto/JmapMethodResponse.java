package com.yxoct.mail.client.stalwart.dto;

import com.yxoct.mail.client.stalwart.jackson.JmapMethodResponseDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = JmapMethodResponseDeserializer.class)
public record JmapMethodResponse(String method, JsonNode response, String callId) {}
