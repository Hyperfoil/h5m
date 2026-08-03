package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.Role;
import io.hyperfoil.tools.h5m.svc.ApiKeyService;
import io.hyperfoil.tools.h5m.svc.SecurityEnabledProfile;
import io.hyperfoil.tools.h5m.svc.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(SecurityEnabledProfile.class)
public class ApiKeyAuthTest extends FreshDb {

    @Inject
    UserService userService;

    @Inject
    ApiKeyService apiKeyService;

    @Test
    void write_endpoint_returns_401_without_auth() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/api/folder/test")
                .then()
                .statusCode(401);
    }

    @Test
    void read_endpoint_returns_200_without_auth() {
        given()
                .when().get("/api/folder")
                .then()
                .statusCode(200);
    }

    @Test
    void write_endpoint_succeeds_with_valid_api_key() {
        userService.create("writer", Role.USER);
        String key = apiKeyService.create("writer", "write key").rawKey();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + key)
                .when().post("/api/folder/auth-test")
                .then()
                .statusCode(200);
    }

    @Test
    void write_endpoint_returns_401_with_invalid_key() {
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer H5M_INVALID_KEY_12345678")
                .when().post("/api/folder/test")
                .then()
                .statusCode(401);
    }

    @Test
    void write_endpoint_returns_401_with_revoked_key() {
        userService.create("revoked-user", Role.USER);
        String key = apiKeyService.create("revoked-user", "revoked key").rawKey();
        var keys = apiKeyService.listByUser("revoked-user");
        apiKeyService.revoke(keys.get(0).id());

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + key)
                .when().post("/api/folder/test")
                .then()
                .statusCode(401);
    }

    @Test
    void purge_endpoint_returns_403_for_non_admin() {
        userService.create("regular", Role.USER);
        String key = apiKeyService.create("regular", "user key").rawKey();

        given()
                .header("Authorization", "Bearer " + key)
                .when().delete("/api/value")
                .then()
                .statusCode(403);
    }

    @Test
    void purge_endpoint_succeeds_for_admin() {
        userService.create("admin", Role.ADMIN);
        String key = apiKeyService.create("admin", "admin key").rawKey();

        given()
                .header("Authorization", "Bearer " + key)
                .when().delete("/api/value")
                .then()
                .statusCode(204);
    }

    // ---- Bootstrap service ----

    @Test
    void bootstrap_creates_admin_with_known_key() {
        // Simulate bootstrap: create admin user + key with known value
        String bootstrapKey = "H5M_TEST_BOOTSTRAP_KEY_12345678";
        userService.create("admin", Role.ADMIN);
        apiKeyService.create("admin", "bootstrap", bootstrapKey);

        // The known key should work for authenticated endpoints
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bootstrapKey)
                .when().post("/api/folder/bootstrap-test")
                .then()
                .statusCode(200);
    }

    @Test
    void bootstrap_key_authenticates() {
        String bootstrapKey = "H5M_BOOTSTRAP_" + java.util.UUID.randomUUID().toString().replace("-", "_").toUpperCase();
        userService.create("bootstrap-admin", Role.ADMIN);
        apiKeyService.create("bootstrap-admin", "bootstrap", bootstrapKey);

        // The bootstrap key should work for authenticated endpoints
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bootstrapKey)
                .when().post("/api/folder/bootstrap-test")
                .then()
                .statusCode(200);
    }

    // ---- API Key REST endpoints ----

    @Test
    void apikey_create_and_list() {
        userService.create("keyuser", Role.USER);
        String authKey = apiKeyService.create("keyuser", "auth key").rawKey();

        // Create a new key via REST — returns ApiKey DTO with rawKey populated
        String newKey = given()
                .header("Authorization", "Bearer " + authKey)
                .queryParam("description", "test key")
                .when().post("/api/apikey")
                .then()
                .statusCode(200)
                .body("rawKey", org.hamcrest.Matchers.startsWith("H5M_"))
                .body("description", org.hamcrest.Matchers.equalTo("test key"))
                .body("id", org.hamcrest.Matchers.notNullValue())
                .extract().path("rawKey");

        org.junit.jupiter.api.Assertions.assertTrue(newKey.startsWith("H5M_"),
                "Created key should start with H5M_ prefix");

        // List keys — should see at least 2 (the auth key + the new one)
        given()
                .header("Authorization", "Bearer " + authKey)
                .when().get("/api/apikey")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2));
    }

    @Test
    void apikey_revoke() {
        userService.create("revokeuser", Role.USER);
        String authKey = apiKeyService.create("revokeuser", "auth key").rawKey();

        // Create a key to revoke
        String keyToRevoke = apiKeyService.create("revokeuser", "will be revoked").rawKey();
        var keys = apiKeyService.listByUser("revokeuser");
        long revokeId = keys.stream()
                .filter(k -> "will be revoked".equals(k.description()))
                .findFirst().orElseThrow().id();

        // Revoke via REST
        given()
                .header("Authorization", "Bearer " + authKey)
                .when().put("/api/apikey/" + revokeId + "/revoke")
                .then()
                .statusCode(204);

        // Verify the revoked key no longer works
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + keyToRevoke)
                .when().post("/api/folder/test")
                .then()
                .statusCode(401);
    }

    @Test
    void apikey_revoke_other_user_forbidden() {
        userService.create("user1", Role.USER);
        userService.create("user2", Role.USER);
        String key1 = apiKeyService.create("user1", "user1 key").rawKey();
        apiKeyService.create("user2", "user2 key");
        var user2Keys = apiKeyService.listByUser("user2");
        long user2KeyId = user2Keys.get(0).id();

        // user1 tries to revoke user2's key — should be forbidden
        given()
                .header("Authorization", "Bearer " + key1)
                .when().put("/api/apikey/" + user2KeyId + "/revoke")
                .then()
                .statusCode(403);
    }

    @Test
    void apikey_revoke_nonexistent_returns_404() {
        userService.create("user404", Role.USER);
        String key = apiKeyService.create("user404", "auth key").rawKey();

        given()
                .header("Authorization", "Bearer " + key)
                .when().put("/api/apikey/999999/revoke")
                .then()
                .statusCode(404);
    }

    @Test
    void apikey_list_does_not_expose_raw_key() {
        userService.create("listuser", Role.USER);
        String key = apiKeyService.create("listuser", "auth key").rawKey();
        apiKeyService.create("listuser", "another key");

        given()
                .header("Authorization", "Bearer " + key)
                .when().get("/api/apikey")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("[0].rawKey", org.hamcrest.Matchers.nullValue())
                .body("[1].rawKey", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void apikey_admin_can_revoke_other_user_key() {
        userService.create("adminuser", Role.ADMIN);
        userService.create("targetuser", Role.USER);
        String adminKey = apiKeyService.create("adminuser", "admin key").rawKey();
        apiKeyService.create("targetuser", "target key");
        var targetKeys = apiKeyService.listByUser("targetuser");
        long targetKeyId = targetKeys.get(0).id();

        // Admin should be able to revoke another user's key
        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().put("/api/apikey/" + targetKeyId + "/revoke")
                .then()
                .statusCode(204);
    }

    @Test
    void apikey_create_empty_description_returns_400() {
        userService.create("emptyuser", Role.USER);
        String key = apiKeyService.create("emptyuser", "auth key").rawKey();

        given()
                .header("Authorization", "Bearer " + key)
                .queryParam("description", "")
                .when().post("/api/apikey")
                .then()
                .statusCode(400);
    }

    @Test
    void apikey_requires_auth() {
        // Create and list should require authentication
        given()
                .queryParam("description", "test")
                .when().post("/api/apikey")
                .then()
                .statusCode(401);

        given()
                .when().get("/api/apikey")
                .then()
                .statusCode(401);
    }
}
