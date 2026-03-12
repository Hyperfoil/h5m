package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.Map;

public interface FolderServiceInterface {

    Folder byName(String name);

    Map<String, Integer> getFolderUploadCount();

    long create(String name);

    long delete(String name);

    void upload(String name, String path, JsonNode data);

    void recalculate(String name);

    Json structure(String name);

}
