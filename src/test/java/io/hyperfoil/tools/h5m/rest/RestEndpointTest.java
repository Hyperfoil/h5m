package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.FreshDb;

import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.ProcessingEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.node.JqNode;
import io.hyperfoil.tools.h5m.entity.node.RootNode;
import io.hyperfoil.tools.h5m.svc.FolderService;
import io.hyperfoil.tools.h5m.svc.ProcessingService;
import io.hyperfoil.tools.h5m.svc.ValueService;
import io.hyperfoil.tools.h5m.svc.WorkService;
import io.restassured.specification.RequestSpecification;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.*;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;


@QuarkusTest
public class RestEndpointTest extends FreshDb {

    /** Serialize a View record to JSON string for REST request bodies. */
    private static String viewToJson(io.hyperfoil.tools.h5m.api.View view) {
        JqValue[] components = view.components() == null ? new JqValue[0] : view.components().stream().map(c -> (JqValue) JqObject.builder()
                .put("id", c.id() != null ? JqNumber.of(c.id()) : JqNull.NULL)
                .put("nodeId", c.nodeId() != null ? JqNumber.of(c.nodeId()) : JqNull.NULL)
                .put("nodeName", c.nodeName() != null ? JqString.of(c.nodeName()) : JqNull.NULL)
                .put("nodeType", c.nodeType() != null ? JqString.of(c.nodeType()) : JqNull.NULL)
                .put("headerName", c.headerName() != null ? JqString.of(c.headerName()) : JqNull.NULL)
                .put("headerOrder", JqNumber.of(c.headerOrder()))
                .build()).toArray(JqValue[]::new);
        return JqObject.builder()
                .put("id", view.id() != null ? JqNumber.of(view.id()) : JqNull.NULL)
                .put("name", JqString.of(view.name()))
                .put("folderId", view.folderId() != null ? JqNumber.of(view.folderId()) : JqNull.NULL)
                .put("components", JqArray.of(components))
                .build().toJsonString();
    }

    @Inject
    TransactionManager tm;

    @Inject
    WorkService workService;

    @Inject
    FolderService folderService;

    @Inject
    ValueService valueService;

    @Inject
    ProcessingService processingService;

    private long createFolder(String name) {
        return given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .when().post("/api/folder")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    private Long getGroupId(String name) {
        return given()
                .queryParam("name", name)
                .when().get("/api/group/find")
                .then()
                .extract().jsonPath().getLong("id");
    }

    private Long createNode(Long groupId, String name, String operation) {
        return createNodeWithType(groupId, name, NodeType.JQ.name(), operation);
    }

    private Long createConfiguredNode(Long groupId, String name, String type, List<Long> sources, String configBody) {
        RequestSpecification request = given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", name)
                .queryParam("groupId", groupId)
                .queryParam("type", type);
        for (Long sourceId : sources) {
            request = request.queryParam("sources", sourceId);
        }
        if (configBody != null) {
            request = request.body(configBody);
        }
        return request
                .when().post("/api/node/configured")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    private Long createNodeWithType(Long groupId, String name, String type, String operation) {
        return given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "groupId", groupId, "type", type, "operation", operation))
                .when().post("/api/node")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    // -- Folder endpoints --

    @Test
    public void folder_create_rejects_reserved_name() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "h5m.reserved"))
                .when().post("/api/folder")
                .then()
                .statusCode(400);
    }

    @Test
    public void node_create_rejects_reserved_name() {
        createFolder("reserved-node-test");
        Long groupId = getGroupId("reserved-node-test");
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "h5m.reserved", "groupId", groupId, "type", NodeType.JQ.name(), "operation", "."))
                .when().post("/api/node")
                .then()
                .statusCode(400);
    }

    @Test
    public void folder_create_and_get() {
        createFolder("test-folder");

        given()
                .queryParam("name", "test-folder")
                .when().get("/api/folder/find")
                .then()
                .statusCode(200)
                .body("name", equalTo("test-folder"))
                .body("id", notNullValue());
    }

    @Test
    public void folder_list_empty() {
        given()
                .when().get("/api/folder")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void folder_list_with_folder() {
        createFolder("list-test");

        given()
                .when().get("/api/folder")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("name", hasItem("list-test"));
    }

    @Test
    public void folder_get_upload_count_empty() {
        given()
                .when().get("/api/folder/count")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void folder_get_upload_count_with_folder() {
        createFolder("count-test");

        given()
                .when().get("/api/folder/count")
                .then()
                .statusCode(200)
                .body("'count-test'", equalTo(0));
    }

    @Test
    public void folder_upload_and_structure() {
        long folderId = createFolder("upload-test");

        given()
                .multiPart("raw", "{\"key\": \"value\"}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200);

        given()
                .when().get("/api/folder/" + folderId + "/structure")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void folder_delete() {
        long folderId = createFolder("delete-me");

        given()
                .when().delete("/api/folder/" + folderId)
                .then()
                .statusCode(204);

        // null return becomes 204 No Content
        given()
                .queryParam("name", "delete-me")
                .when().get("/api/folder/find")
                .then()
                .statusCode(204);
    }

    // -- Node endpoints --

    @Test
    public void node_create_and_find() {
        createFolder("node-test");
        Long groupId = getGroupId("node-test");
        createNode(groupId, "jq-node", ".foo");

        // use asString() since Node->NodeGroup->List<Node> has circular refs (see #47)
        String response = given()
                .queryParam("name", "jq-node")
                .queryParam("groupId", groupId)
                .when().get("/api/node/find")
                .then()
                .statusCode(200)
                .extract().asString();

        assertTrue(response.contains("jq-node"), "Response should contain the node name");
    }

    @Test
    public void node_delete() {
        createFolder("node-del-test");
        Long groupId = getGroupId("node-del-test");
        Long nodeId = createNode(groupId, "to-delete", ".bar");

        given()
                .when().delete("/api/node/" + nodeId)
                .then()
                .statusCode(204);

        given()
                .queryParam("name", "to-delete")
                .queryParam("groupId", groupId)
                .when().get("/api/node/find")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void node_update() {
        createFolder("node-update-test");
        Long groupId = getGroupId("node-update-test");
        Long nodeId = createNode(groupId, "updatable", ".foo");

        // Update operation — should return the updated Node
        given()
                .queryParam("operation", ".bar")
                .queryParam("name", "updatable")
                .when().put("/api/node/" + nodeId)
                .then()
                .statusCode(200)
                .body("id", equalTo(nodeId.intValue()));
    }

    // -- NodeGroup endpoints --

    @Test
    public void group_get_by_name() {
        createFolder("group-test");

        given()
                .queryParam("name", "group-test")
                .when().get("/api/group/find")
                .then()
                .statusCode(200)
                .body("name", equalTo("group-test"))
                .body("id", notNullValue());
    }

    @Test
    public void group_get_nonexistent() {
        given()
                .queryParam("name", "nonexistent")
                .when().get("/api/group/find")
                .then()
                .statusCode(204);
    }

    // -- Value endpoints --

    @Test
    public void value_get_descendants_empty() throws Exception {
        tm.begin();
        RootNode rootNode = new RootNode();
        rootNode.persist();
        tm.commit();

        given()
                .when().get("/api/value/node/" + rootNode.id + "/descendants")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void value_get_descendants_with_data() throws Exception {
        tm.begin();
        RootNode rootNode = new RootNode();
        rootNode.persist();
        JqNode jqNode = new JqNode("child", ".foo");
        jqNode.sources = List.of(rootNode);
        jqNode.persist();
        ValueEntity rootValue = new ValueEntity(null, rootNode, JqValues.parse("{\"foo\": \"bar\"}"));
        rootValue.persist();
        ValueEntity childValue = new ValueEntity(null, jqNode, JqValues.parse("\"bar\""));
        childValue.sources = List.of(rootValue);
        childValue.persist();
        tm.commit();

        given()
                .when().get("/api/value/node/" + rootNode.id + "/descendants")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    public void value_purge() throws Exception {
        tm.begin();
        RootNode rootNode = new RootNode();
        rootNode.persist();
        ValueEntity rootValue = new ValueEntity(null, rootNode, JqValues.parse("{\"a\": 1}"));
        rootValue.persist();
        tm.commit();

        given()
                .when().delete("/api/value")
                .then()
                .statusCode(204);

        given()
                .when().get("/api/value/node/" + rootNode.id + "/descendants")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    // -- Node createConfigured endpoint --

    @Test
    public void node_create_configured_fixed_threshold() {
        createFolder("ft-test");
        Long groupId = getGroupId("ft-test");
        Long rangeNodeId = createNode(groupId, "range", ".y");
        Long fpNodeId = createNode(groupId, "fingerprint", ".fp");

        Long nodeId = createConfiguredNode(groupId, "threshold", NodeType.FIXED_THRESHOLD.name(),
                List.of(fpNodeId, rangeNodeId),
                """
                {"min": 10.0, "max": 100.0, "minInclusive": true, "maxInclusive": true, "fingerprintFilter": null}
                """);

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    @Test
    public void node_create_configured_relative_difference() {
        createFolder("rd-test");
        Long groupId = getGroupId("rd-test");
        Long rangeNodeId = createNode(groupId, "range", ".y");
        Long fpNodeId = createNode(groupId, "fingerprint", ".fp");

        Long nodeId = createConfiguredNode(groupId, "reldiff", NodeType.RELATIVE_DIFFERENCE.name(),
                List.of(fpNodeId, rangeNodeId),
                """
                {"filter": "MAX", "threshold": 0.2, "window": 5, "minPrevious": 3, "fingerprintFilter": null}
                """);

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    @Test
    public void node_create_configured_stddev_anomaly() {
        createFolder("sd-test");
        Long groupId = getGroupId("sd-test");
        Long rangeNodeId = createNode(groupId, "range", ".y");
        Long fpNodeId = createNode(groupId, "fingerprint", ".fp");

        Long nodeId = createConfiguredNode(groupId, "stddev", NodeType.STDDEV_ANOMALY.name(),
                List.of(fpNodeId, rangeNodeId),
                """
                {"windowSize": 40, "deviations": 4.0, "direction": "BOTH", "minDataPoints": 10, "fingerprintFilter": null}
                """);

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    @Test
    public void node_create_configured_fingerprint() {
        createFolder("fp-test");
        Long groupId = getGroupId("fp-test");
        Long jqNodeId = createNode(groupId, "source", ".foo");

        Long nodeId = createConfiguredNode(groupId, "fp-node", NodeType.FINGERPRINT.name(),
                List.of(jqNodeId), null);

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    // -- Node create with other types --

    @Test
    public void node_create_jsonata() {
        createFolder("jsonata-test");
        Long groupId = getGroupId("jsonata-test");

        Long nodeId = createNodeWithType(groupId, "jsonata-node", NodeType.JSONATA.name(), "$sum(foo)");

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    @Test
    public void node_create_jq_from_jsonpath() {
        createFolder("jq-jsonpath-test");
        Long groupId = getGroupId("jq-jsonpath-test");

        Long nodeId = createNodeWithType(groupId, "jq-node", NodeType.JQ.name(), ".foo.bar");

        assertTrue(nodeId > 0, "should return a valid node ID");
    }

    // -- End-to-end: upload + computed values --

    @Test
    public void upload_and_verify_jq_values_via_rest() throws InterruptedException {
        long folderId = createFolder("e2e-test");
        Long groupId = getGroupId("e2e-test");
        Long jqNodeId = createNode(groupId, "extract", ".key");

        // Upload data via REST
        given()
                .multiPart("raw", "{\"key\": \"hello\"}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200);

        // Wait for the work queue to finish processing the upload
        long deadline = System.currentTimeMillis() + 10_000;
        while (!workService.isIdle() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(workService.isIdle(), "Work queue should be idle after processing");

        // Verify the JQ node computed a value from the uploaded data via REST
        given()
                .when().get("/api/value/node/" + jqNodeId)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].data", equalTo("hello"));
    }

    // -- Dashboard summaries --

    @Test
    public void dashboard_returns_empty_list_when_no_folders() {
        given()
                .when().get("/api/folder/dashboard")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void dashboard_returns_folder_summary_with_rhivos_upload() throws Exception {
        // Import rhivos node graph and upload a run
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        try (InputStream is = getClass().getResourceAsStream("/rhivos/40375.json")) {
            JqValue runData = JqValues.parse(is.readAllBytes());
            processingService.awaitIngestion(valueService.createRootValue(rhivosFolderId, runData), 30, TimeUnit.SECONDS);
        }

        given()
                .when().get("/api/folder/dashboard")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("rhivos-perf-comprehensive"))
                .body("[0].uploadCount", equalTo(1))
                .body("[0].nodeCount", greaterThan(100))
                .body("[0].lastUpload", notNullValue());
    }

    @Test
    public void dashboard_counts_multiple_uploads() throws Exception {
        // Import rhivos node graph and upload two runs
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        for (String runFile : List.of("/rhivos/40375.json", "/rhivos/40376.json")) {
            try (InputStream is = getClass().getResourceAsStream(runFile)) {
                JqValue runData = JqValues.parse(is.readAllBytes());
                processingService.awaitIngestion(valueService.createRootValue(rhivosFolderId, runData), 30, TimeUnit.SECONDS);
            }
        }

        given()
                .when().get("/api/folder/dashboard")
                .then()
                .statusCode(200)
                .body("[0].uploadCount", equalTo(2));
    }

    // -- Value data endpoint --

    @Test
    public void value_data_returns_json_for_uploaded_rhivos_run() throws Exception {
        // Import rhivos node graph and upload a run
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        try (InputStream is = getClass().getResourceAsStream("/rhivos/40375.json")) {
            JqValue runData = JqValues.parse(is.readAllBytes());
            processingService.awaitIngestion(valueService.createRootValue(rhivosFolderId, runData), 30, TimeUnit.SECONDS);
        }

        // Find the root value ID — the upload itself
        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        List<ValueEntity> rootValues = ValueEntity.find("node.id", folder.group.root.id).list();
        Long valueId = rootValues.get(0).id;
        tm.commit();

        // Verify the endpoint returns the uploaded JSON data
        given()
                .when().get("/api/value/" + valueId)
                .then()
                .statusCode(200)
                // rhivos runs have metadata with user and uuid fields
                .body("metadata.user", notNullValue());
    }

    @Test
    public void value_data_returns_404_for_nonexistent_value() {
        given()
                .when().get("/api/value/999999")
                .then()
                .statusCode(404);
    }

    // -- Views --

    @Test
    public void view_default_created_for_new_folder() {
        long folderId = createFolder("view-default");

        given()
                .when().get("/api/folder/" + folderId + "/view/")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("h5m.default"))
                .body("[0].components.size()", equalTo(0));
    }

    @Test
    public void view_default_created_on_import_empty() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        // Default view should exist but be empty (users configure it)
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("h5m.default"))
                .body("[0].components.size()", equalTo(0));
    }

    @Test
    public void view_create_and_retrieve() throws Exception {
        // Import rhivos nodes so we have real node IDs to reference
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        // Find node IDs for "user" and "uuid" by querying the group
        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long userNodeId = folder.group.sources.stream()
                .filter(n -> "user".equals(n.name)).findFirst().get().id;
        Long uuidNodeId = folder.group.sources.stream()
                .filter(n -> "uuid".equals(n.name)).findFirst().get().id;
        tm.commit();

        // Create a view via REST
        String viewJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "test-view", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, userNodeId, null, null, "User", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, uuidNodeId, null, null, "UUID", 1)
                )
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(viewJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .body("name", equalTo("test-view"))
                .body("components.size()", equalTo(2))
                .body("components[0].headerName", equalTo("User"))
                .body("components[1].headerName", equalTo("UUID"))
                .extract().jsonPath().getLong("id");

        // Retrieve it
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/" + viewId)
                .then()
                .statusCode(200)
                .body("name", equalTo("test-view"))
                .body("components.size()", equalTo(2));

        // List should contain Default + test-view
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2));
    }

    @Test
    public void view_update_changes_components() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long userNodeId = folder.group.sources.stream()
                .filter(n -> "user".equals(n.name)).findFirst().get().id;
        Long uuidNodeId = folder.group.sources.stream()
                .filter(n -> "uuid".equals(n.name)).findFirst().get().id;
        Long descNodeId = folder.group.sources.stream()
                .filter(n -> "description".equals(n.name)).findFirst().get().id;
        tm.commit();

        // Create view with user only
        String createJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "updatable", null,
                List.of(new io.hyperfoil.tools.h5m.api.ViewComponent(null, userNodeId, null, null, "User", 0))
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        // Update to uuid + description
        String updateJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "updatable-renamed", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, uuidNodeId, null, null, "UUID", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, descNodeId, null, null, "Description", 1)
                )
        ));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateJson)
                .when().put("/api/folder/" + rhivosFolderId + "/view/" + viewId)
                .then()
                .statusCode(200)
                .body("name", equalTo("updatable-renamed"))
                .body("components.size()", equalTo(2))
                .body("components[0].headerName", equalTo("UUID"));
    }

    @Test
    public void view_update_with_same_header_names() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long userNodeId = folder.group.sources.stream()
                .filter(n -> "user".equals(n.name)).findFirst().get().id;
        Long uuidNodeId = folder.group.sources.stream()
                .filter(n -> "uuid".equals(n.name)).findFirst().get().id;
        tm.commit();

        // Create view with user and uuid
        String createJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "same-headers", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, userNodeId, null, null, "User", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, uuidNodeId, null, null, "UUID", 1)
                )
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        // Update with the same header names but reversed order — this previously
        // caused a unique constraint violation because Hibernate inserted new
        // components before deleting old ones with the same (view_id, header_name)
        String updateJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "same-headers", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, uuidNodeId, null, null, "UUID", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, userNodeId, null, null, "User", 1)
                )
        ));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateJson)
                .when().put("/api/folder/" + rhivosFolderId + "/view/" + viewId)
                .then()
                .statusCode(200)
                .body("components.size()", equalTo(2))
                .body("components[0].headerName", equalTo("UUID"))
                .body("components[0].headerOrder", equalTo(0))
                .body("components[1].headerName", equalTo("User"))
                .body("components[1].headerOrder", equalTo(1));
    }

    @Test
    public void view_delete_works() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long userNodeId = folder.group.sources.stream()
                .filter(n -> "user".equals(n.name)).findFirst().get().id;
        tm.commit();

        String createJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "deletable", null,
                List.of(new io.hyperfoil.tools.h5m.api.ViewComponent(null, userNodeId, null, null, "User", 0))
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        // Delete it
        given()
                .when().delete("/api/folder/" + rhivosFolderId + "/view/" + viewId)
                .then()
                .statusCode(204);

        // Custom view should be gone, only Default remains
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("h5m.default"));
    }

    @Test
    public void view_delete_default_rejected() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        // Find the auto-created Default view's ID
        Long viewId = given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/")
                .then()
                .statusCode(200)
                .body("[0].name", equalTo("h5m.default"))
                .extract().jsonPath().getLong("[0].id");

        // Attempting to delete the system default view is forbidden
        given()
                .when().delete("/api/folder/" + rhivosFolderId + "/view/" + viewId)
                .then()
                .statusCode(403);
    }

    @Test
    public void view_data_returns_filtered_rhivos_values() throws Exception {
        // Import rhivos nodes
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        // Create the view BEFORE uploading — nodes referenced by views are
        // excluded from ephemeral nullification, so this must happen first
        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long startTimeNodeId = folder.group.sources.stream()
                .filter(n -> "start_time".equals(n.name) && n.sources.contains(folder.group.root))
                .findFirst().get().id;
        Long endTimeNodeId = folder.group.sources.stream()
                .filter(n -> "end_time".equals(n.name)).findFirst().get().id;
        tm.commit();

        // Create a view with start_time and end_time
        String viewJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "data-view", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, startTimeNodeId, null, null, "Start Time", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, endTimeNodeId, null, null, "End Time", 1)
                )
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(viewJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        // Upload a run — ephemeral nullification will skip start_time/end_time
        // because they are referenced by the view
        try (InputStream is = getClass().getResourceAsStream("/rhivos/40375.json")) {
            JqValue runData = JqValues.parse(is.readAllBytes());
            processingService.awaitIngestion(valueService.createRootValue(rhivosFolderId, runData), 30, TimeUnit.SECONDS);
        }

        // Get view data — should return filtered results with only start_time and end_time columns
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/" + viewId + "/data")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].start_time", notNullValue())
                .body("[0].end_time", notNullValue());
    }

    @Test
    public void view_data_with_multiple_uploads() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        // Create the view BEFORE uploading — nodes referenced by views are
        // excluded from ephemeral nullification
        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long startTimeNodeId = folder.group.sources.stream()
                .filter(n -> "start_time".equals(n.name) && n.sources.contains(folder.group.root))
                .findFirst().get().id;
        tm.commit();

        String viewJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "multi-upload-view", null,
                List.of(new io.hyperfoil.tools.h5m.api.ViewComponent(null, startTimeNodeId, null, null, "Start Time", 0))
        ));

        Long viewId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(viewJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        // Upload two runs — start_time data preserved because it's in a view
        for (String runFile : List.of("/rhivos/40375.json", "/rhivos/40376.json")) {
            try (InputStream is = getClass().getResourceAsStream(runFile)) {
                JqValue runData = JqValues.parse(is.readAllBytes());
                processingService.awaitIngestion(valueService.createRootValue(rhivosFolderId, runData), 30, TimeUnit.SECONDS);
            }
        }

        // View data should have one row per upload
        given()
                .when().get("/api/folder/" + rhivosFolderId + "/view/" + viewId + "/data")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].start_time", notNullValue())
                .body("[1].start_time", notNullValue());
    }

    @Test
    public void view_component_ordering() throws Exception {
        folderService.importFolder(Path.of("src/test/resources/rhivos/nodes.json"), false);
        long rhivosFolderId = folderService.find("rhivos-perf-comprehensive").id();

        tm.begin();
        FolderEntity folder = FolderEntity.find("name", "rhivos-perf-comprehensive").firstResult();
        Long startTimeNodeId = folder.group.sources.stream()
                .filter(n -> "start_time".equals(n.name) && n.sources.contains(folder.group.root))
                .findFirst().get().id;
        Long endTimeNodeId = folder.group.sources.stream()
                .filter(n -> "end_time".equals(n.name)).findFirst().get().id;
        tm.commit();

        // Create view with end_time at order 0 and start_time at order 1 (reversed)
        String viewJson = viewToJson(new io.hyperfoil.tools.h5m.api.View(
                null, "ordered-view", null,
                List.of(
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, endTimeNodeId, null, null, "End", 0),
                        new io.hyperfoil.tools.h5m.api.ViewComponent(null, startTimeNodeId, null, null, "Start", 1)
                )
        ));

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(viewJson)
                .when().post("/api/folder/" + rhivosFolderId + "/view")
                .then()
                .statusCode(200)
                // Components should be ordered by headerOrder
                .body("components[0].headerName", equalTo("End"))
                .body("components[0].headerOrder", equalTo(0))
                .body("components[1].headerName", equalTo("Start"))
                .body("components[1].headerOrder", equalTo(1));
    }

    // -- OpenAPI spec --

    @Test
    public void openapi_spec_available() {
        given()
                .when().get("/q/openapi")
                .then()
                .statusCode(200)
                .body(containsString("/api/folder"))
                .body(containsString("/api/node"))
                .body(containsString("/api/group"))
                .body(containsString("/api/value"));
    }

    @Test
    public void labelValues_returns_grouped_values() throws InterruptedException {
        Long folderId = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "lv-test"))
                .when().post("/api/folder")
                .then()
                .statusCode(200)
                .extract().jsonPath().getLong("id");

        Long groupId = getGroupId("lv-test");
        Long throughputId=createNode(groupId, "throughput", ".throughput");
        Long buildId= createNode(groupId, "build_id", ".build_id");

        given().multiPart("raw", "{\"throughput\": 115, \"build_id\": 201}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then().statusCode(200);

        given().multiPart("raw", "{\"throughput\": 100, \"build_id\": 202}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then().statusCode(200);

        long deadline = System.currentTimeMillis() + 10_000;
        while (!workService.isIdle() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(workService.isIdle(), "Work queue should be idle");

        given().when().get("/api/folder/"+folderId+"/labelValues")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("throughput", hasItems(100,115))
                .body("build_id", hasItems(201,202));

        given().queryParam("sortById", throughputId)
                .queryParam("nodeIds",throughputId)
                .when().get("/api/folder/"+folderId+"/labelValues")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].throughput", equalTo(100))
                .body("[1].throughput", equalTo(115));
    }

    @Test
    public void labelValues_folder_not_found_returns_404() {
        given()
                .when().get("/api/folder/123/labelValues")
                .then()
                .statusCode(404);
    }

    @Test
    public void upload_returns_uploadId() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        long folderId = createFolder("upload-id-test");

        Long uploadId = given()
                .multiPart("raw", "{\"cpu\": 95}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        assertTrue(uploadId > 0, "uploadId should be a positive number");

        tm.begin();
        ProcessingEntity entity = ProcessingEntity
                .find("valueId", uploadId)
                .firstResult();
        assertNotNull(entity, "ProcessingEntity should exist for the returned uploadId");
        assertEquals(uploadId, entity.valueId, "valueId in DB should match the returned uploadId");
        tm.commit();

    }

    @Test
    public void FolderUpload_empty_body_returns_error() {
        long folderId = createFolder("test-empty");
        given()
                .multiPart("raw", "")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(400);
    }

    @Test
    public void FolderUpload_tracking_record_created() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        long folderId = createFolder("test-tracking");
        Long uploadId = given()
                .multiPart("raw", "{\"cpu\": 95}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        tm.begin();
        ProcessingEntity entity = ProcessingEntity.find("valueId", uploadId).firstResult();
        tm.commit();
        assertNotNull(entity);
        assertTrue(entity.completed);
    }

    @Test
    public void Upload_nonExistingFolder_returns_error() {
        given()
                .multiPart("raw", "{\"cpu\": 95}")
                .when().post("/api/folder/999999/upload")
                .then()
                .statusCode(400);
    }

    @Test
    public void multiple_uploads_return_unique_ids() {
        long folderId = createFolder("upload-unique-ids-test");

        Long uploadId1 = given()
                .multiPart("raw", "{\"cpu\": 95}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        Long uploadId2 = given()
                .multiPart("raw", "{\"cpu\": 99}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        assertNotEquals(uploadId1, uploadId2, "Each upload should return a unique ID");
    }

    // ---- Upload status tracking ----

    @Test
    public void upload_status_empty_folder() {
        long folderId = createFolder("upload-status-empty");

        Long uploadId = given()
                .multiPart("raw", "{\"value\": 42}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        // The upload should complete quickly (no nodes to process)
        // Poll the processing status endpoint
        given()
                .when().get("/api/processing/upload/" + uploadId)
                .then()
                .statusCode(200)
                .body("state", equalTo("COMPLETED"));
    }

    @Test
    public void upload_status_not_found() {
        given()
                .when().get("/api/processing/999999")
                .then()
                .statusCode(404);
    }

    @Test
    public void upload_status() {
        long folderId = createFolder("upload-status");
        Long groupId = getGroupId("upload-status");
        createNode(groupId, "extract", ".value");

        Long uploadId = given()
                .multiPart("raw", "{\"value\": 42}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        // Status should eventually be COMPLETED (processing is fast for simple nodes)
        // Allow a brief wait for async processing
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        given()
                .when().get("/api/processing/upload/" + uploadId)
                .then()
                .statusCode(200)
                .body("state", equalTo("COMPLETED"));
    }

    @Test
    public void upload_status_with_change_detection() throws Exception {
        // Set up a folder with extractor nodes and a FixedThreshold detection node.
        // Upload data that violates the threshold via REST and verify the upload
        // status endpoint reports the change detection result.
        long folderId = createFolder("ft-detect");
        Long groupId = getGroupId("ft-detect");

        // Create JQ extractor nodes — these source from the root node automatically
        Long rangeNodeId = createNode(groupId, "range", ".value");
        Long fpExtractorId = createNode(groupId, "fp-extractor", ".env");

        // Get the root node ID to use as groupBy for the FixedThreshold
        tm.begin();
        FolderEntity folder = FolderEntity.findById(folderId);
        Long rootNodeId = folder.group.root.id;
        tm.commit();

        // Create FixedThreshold: min=10, max=100 (inclusive)
        // Sources order: [fingerprint(0), groupBy(1), range(2)]
        Long ftNodeId = createConfiguredNode(groupId, "cpu-threshold",
                NodeType.FIXED_THRESHOLD.name(),
                List.of(fpExtractorId, rootNodeId, rangeNodeId),
                """
                {"min": 10.0, "max": 100.0, "minInclusive": true, "maxInclusive": true}
                """);
        assertTrue(ftNodeId > 0, "FixedThreshold node should be created");

        // Upload data via REST with value=5 — below min=10, should trigger a threshold violation
        Long uploadId = given()
                .multiPart("raw", "{\"value\": 5, \"env\": {\"type\": \"perf-test\"}}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        // Poll processing status until COMPLETED (async processing)
        awaitIngestionCompleted(uploadId);

        // Verify detection values via the descendants endpoint
        given()
                .when().get("/api/value/" + uploadId + "/descendants?detection=true")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].node.type", equalTo("FIXED_THRESHOLD"))
                .body("[0].node.name", equalTo("cpu-threshold"));
    }

    @Test
    public void upload_status_no_change_when_within_threshold() throws Exception {
        // Same setup as upload_status_with_change_detection, but upload data
        // that is within the threshold range — no change detection should fire.
        long folderId = createFolder("ft-no-change");
        Long groupId = getGroupId("ft-no-change");

        Long rangeNodeId = createNode(groupId, "range", ".value");
        Long fpExtractorId = createNode(groupId, "fp-extractor", ".env");

        tm.begin();
        FolderEntity folder = FolderEntity.findById(folderId);
        Long rootNodeId = folder.group.root.id;
        tm.commit();

        // Create FixedThreshold: min=10, max=100 (inclusive)
        createConfiguredNode(groupId, "cpu-threshold",
                NodeType.FIXED_THRESHOLD.name(),
                List.of(fpExtractorId, rootNodeId, rangeNodeId),
                """
                {"min": 10.0, "max": 100.0, "minInclusive": true, "maxInclusive": true}
                """);

        // Upload data via REST with value=50 — within [10, 100], no violation expected
        Long uploadId = given()
                .multiPart("raw", "{\"value\": 50, \"env\": {\"type\": \"perf-test\"}}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        // Poll processing status until COMPLETED
        awaitIngestionCompleted(uploadId);

        // Verify no detection values — value is within threshold
        given()
                .when().get("/api/value/" + uploadId + "/descendants?detection=true")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    /**
     * After the upload tracker is cleaned up, the processing endpoint infers
     * COMPLETED from the root value existing in DB, and the descendants endpoint
     * always queries DB (with cache as fast path).
     */
    @Test
    public void upload_status_db_fallback_after_cache_eviction() throws Exception {
        // Same setup as upload_status_with_change_detection — create a folder
        // with a FixedThreshold that will fire, then evict the in-memory cache
        // to force the DB fallback path.
        long folderId = createFolder("ft-detect-fallback");
        Long groupId = getGroupId("ft-detect-fallback");

        Long rangeNodeId = createNode(groupId, "range", ".value");
        Long fpExtractorId = createNode(groupId, "fp-extractor", ".env");

        tm.begin();
        FolderEntity folder = FolderEntity.findById(folderId);
        Long rootNodeId = folder.group.root.id;
        tm.commit();

        Long ftNodeId = createConfiguredNode(groupId, "cpu-threshold",
                NodeType.FIXED_THRESHOLD.name(),
                List.of(fpExtractorId, rootNodeId, rangeNodeId),
                """
                {"min": 10.0, "max": 100.0, "minInclusive": true, "maxInclusive": true}
                """);

        // Upload data with value=5 — below min=10, triggers a violation
        Long uploadId = given()
                .multiPart("raw", "{\"value\": 5, \"env\": {\"type\": \"perf-test\"}}")
                .when().post("/api/folder/" + folderId + "/upload")
                .then()
                .statusCode(200)
                .extract().as(Long.class);

        awaitIngestionCompleted(uploadId);

        // Verify processing status is COMPLETED
        given()
                .when().get("/api/processing/upload/" + uploadId)
                .then()
                .statusCode(200)
                .body("state", equalTo("COMPLETED"));

        // Query detection descendants — DB is the source of truth
        given()
                .when().get("/api/value/" + uploadId + "/descendants?detection=true")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].node.name", equalTo("cpu-threshold"))
                .body("[0].node.type", equalTo("FIXED_THRESHOLD"))
                .body("[0].data", notNullValue());
    }

    private void awaitIngestionCompleted(Long uploadId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String state = given()
                    .when().get("/api/processing/upload/" + uploadId)
                    .then()
                    .statusCode(200)
                    .extract().path("state");
            if (!"RUNNING".equals(state)) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Upload " + uploadId + " did not complete within 30 seconds");
    }

    // -- Validation tests for detection config constraints --

    @Test
    public void node_eDivisive_invalid_windowLen_below_minimum() {
        createFolder("eDivisive-test");
        Long groupId = getGroupId("eDivisive-test");
        Long sourceId = createNode(groupId, "source", ".data");

        String invalidConfig = JqObject.builder()
                .put("windowLen", JqNumber.of(2))  // Fails @Min(3)
                .put("maxPvalue", JqNumber.of(0.001))
                .put("minMagnitude", JqNumber.of(0.0))
                .put("maxSeriesLength", JqNumber.of(500))
                .build().toJsonString();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", "eDivisive-invalid")
                .queryParam("groupId", groupId)
                .queryParam("type", "EDIVISIVE")
                .queryParam("sources", sourceId)
                .body(invalidConfig)
                .when().post("/api/node/configured")
                .then()
                .statusCode(400);
    }

    @Test
    public void node_relativeDifference_non_unique_sources() {
        createFolder("relDiff-nonunique");
        Long groupId = getGroupId("relDiff-nonunique");
        Long source1 = createNode(groupId, "source1", ".data");
        Long source2 = createNode(groupId, "source2", ".domain");

        String validConfig = JqObject.builder()
                .put("filter", JqString.of("MAX"))
                .put("threshold", JqNumber.of(0.1))
                .put("window", JqNumber.of(10))
                .put("minPrevious", JqNumber.of(5))
                .build().toJsonString();

        // Try to create with duplicate source (source1 twice) - should fail @UniqueElements
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", "relDiff-nonunique")
                .queryParam("groupId", groupId)
                .queryParam("type", "RELATIVE_DIFFERENCE")
                .queryParam("sources", source1)
                .queryParam("sources", source2)
                .queryParam("sources", source1)  // Duplicate
                .body(validConfig)
                .when().post("/api/node/configured")
                .then()
                .statusCode(400);
    }

}
