package com.yxoct.mail.client.stalwart.jackson;

import com.yxoct.mail.client.stalwart.dto.JmapMethodResponse;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class JmapMethodResponseDeserializer extends ValueDeserializer<JmapMethodResponse> {

  @Override
  public JmapMethodResponse deserialize(JsonParser parser, DeserializationContext context) {

    JsonNode node = parser.readValueAsTree();

    return new JmapMethodResponse(
        node.get(0).stringValue(), node.get(1), node.get(2).stringValue());
  }
}
