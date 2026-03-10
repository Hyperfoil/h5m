package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.api.Value;

import java.util.List;

public interface ValueServiceInterface {

    void purgeValues();

    List<Value> getNodeDescendantValues(Long nodeId);

    List<JsonNode> getGroupedValues(Long nodeId);

}
