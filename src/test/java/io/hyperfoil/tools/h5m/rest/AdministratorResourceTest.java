package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.svc.ApiKeyService;
import io.hyperfoil.tools.h5m.svc.SecurityEnabledProfile;
import io.hyperfoil.tools.h5m.svc.UserService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(SecurityEnabledProfile.class)
public class AdministratorResourceTest extends FreshDb {

    @Inject
    UserService userService;

    @Inject
    ApiKeyService apiKeyService;

    private String keyFor(String username, Role role) {
        userService.create(username, role);
        return apiKeyService.create(username, username + " key").rawKey();
    }

    @Test
    void list_administrators_returns_only_admins() {
        String adminKey = keyFor("admin", Role.ADMIN);
        userService.create("regular", Role.USER);

        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().get("/api/user/administrators")
                .then()
                .statusCode(200)
                .body("username", contains("admin"));
    }

    @Test
    void list_administrators_returns_401_without_auth() {
        given()
                .when().get("/api/user/administrators")
                .then()
                .statusCode(401);
    }

    @Test
    void non_admin_cannot_list_administrators() {
        String userKey = keyFor("regular", Role.USER);
        userService.create("admin", Role.ADMIN);

        given()
                .header("Authorization", "Bearer " + userKey)
                .when().get("/api/user/administrators")
                .then()
                .statusCode(403);
    }

    @Test
    void admin_grants_and_revokes_the_role() {
        String adminKey = keyFor("admin", Role.ADMIN);
        long regularId = userService.create("regular", Role.USER);

        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().put("/api/user/administrators/" + regularId)
                .then()
                .statusCode(200)
                .body("username", containsInAnyOrder("admin", "regular"));
        assertEquals(Role.ADMIN, userService.byUsername("regular").role());

        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().delete("/api/user/administrators/" + regularId)
                .then()
                .statusCode(200)
                .body("username", contains("admin"));
        assertEquals(Role.USER, userService.byUsername("regular").role());
    }

    @Test
    void non_admin_cannot_grant_the_role() {
        String userKey = keyFor("regular", Role.USER);
        long otherId = userService.create("other", Role.USER);

        given()
                .header("Authorization", "Bearer " + userKey)
                .when().put("/api/user/administrators/" + otherId)
                .then()
                .statusCode(403);
        assertEquals(Role.USER, userService.byUsername("other").role());
    }

    @Test
    void admin_cannot_revoke_own_role() {
        String adminKey = keyFor("admin", Role.ADMIN);
        long adminId = userService.byUsername("admin").id();

        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().delete("/api/user/administrators/" + adminId)
                .then()
                .statusCode(400);
        assertEquals(Role.ADMIN, userService.byUsername("admin").role());
    }

    @Test
    void unknown_user_returns_404() {
        String adminKey = keyFor("admin", Role.ADMIN);

        given()
                .header("Authorization", "Bearer " + adminKey)
                .when().put("/api/user/administrators/999999")
                .then()
                .statusCode(404);
    }
}
