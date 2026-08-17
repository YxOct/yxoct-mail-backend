package com.yxoct.mail.client.stalwart.jackson;

import com.yxoct.mail.client.stalwart.dto.JmapMethodResponse;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdDeserializer;

public class JmapMethodResponseDeserializer extends StdDeserializer<JmapMethodResponse> {

  private final ObjectMapper objectMapper;

  public JmapMethodResponseDeserializer(ObjectMapper objectMapper) {

    super(JmapMethodResponse.class);
    this.objectMapper = objectMapper;
  }

  @Override
  public JmapMethodResponse deserialize(JsonParser parser, DeserializationContext context) {

    JsonNode node = objectMapper.readTree(parser);

    return new JmapMethodResponse(
        node.get(0).stringValue(),
        objectMapper.treeToValue(node.get(1), Object.class),
        node.get(2).stringValue());
  }
}
