package io.hyperfoil.tools.h5m.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record Value(Long id, JsonNode data, Node node, Folder folder) {
}
