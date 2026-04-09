package io.hyperfoil.tools.h5m.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.node.JqNode;
import io.hyperfoil.tools.h5m.entity.node.RootNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.*;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class RestEndpointTest extends FreshDb {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Inject
    TransactionManager tm;

    private void createFolder(String name) {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/api/folder/" + name)
                .then()
                .statusCode(200);
    }

    private Long getGroupId(String name) {
        return given()
                .when().get("/api/group/" + name)
                .then()
                .extract().jsonPath().getLong("id");
    }

    private Long createNode(Long groupId, String name, String operation) {
        return given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", name)
                .queryParam("groupId", groupId)
                .queryParam("type", "JQ")
                .queryParam("operation", operation)
                .when().post("/api/node")
                .then()
                .extract().as(Long.class);
    }

    // -- Folder endpoints --

    @Test
    public void folder_create_and_get() {
        createFolder("test-folder");

        given()
                .when().get("/api/folder/test-folder")
                .then()
                .statusCode(200)
                .body("name", equalTo("test-folder"))
                .body("id", notNullValue());
    }

    @Test
    public void folder_get_upload_count_empty() {
        given()
                .when().get("/api/folder")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    public void folder_get_upload_count_with_folder() {
        createFolder("count-test");

        given()
                .when().get("/api/folder")
                .then()
                .statusCode(200)
                .body("count-test", equalTo(0));
    }

    @Test
    public void folder_upload_and_structure() {
        createFolder("upload-test");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("path", "results.json")
                .body("{\"key\": \"value\"}")
                .when().post("/api/folder/upload-test/upload")
                .then()
                .statusCode(204);

        given()
                .when().get("/api/folder/upload-test/structure")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void folder_delete() {
        createFolder("delete-me");

        given()
                .when().delete("/api/folder/delete-me")
                .then()
                .statusCode(200);

        // null return becomes 204 No Content
        given()
                .when().get("/api/folder/delete-me")
                .then()
                .statusCode(204);
    }

    @Test
    public void folder_recalculate() {
        createFolder("recalc-test");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/api/folder/recalc-test/recalculate")
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

    // -- NodeGroup endpoints --

    @Test
    public void group_get_by_name() {
        createFolder("group-test");

        given()
                .when().get("/api/group/group-test")
                .then()
                .statusCode(200)
                .body("name", equalTo("group-test"))
                .body("id", notNullValue());
    }

    @Test
    public void group_get_nonexistent() {
        given()
                .when().get("/api/group/nonexistent")
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
        ValueEntity rootValue = new ValueEntity(null, rootNode, mapper.readTree("{\"foo\": \"bar\"}"));
        rootValue.persist();
        ValueEntity childValue = new ValueEntity(null, jqNode, mapper.readTree("\"bar\""));
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
        ValueEntity rootValue = new ValueEntity(null, rootNode, mapper.readTree("{\"a\": 1}"));
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
}
