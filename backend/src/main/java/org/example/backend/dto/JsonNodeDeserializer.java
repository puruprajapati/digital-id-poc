package org.example.backend.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class JsonNodeDeserializer extends JsonDeserializer<JsonNode> {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    return mapper.readTree(p);
  }
}

