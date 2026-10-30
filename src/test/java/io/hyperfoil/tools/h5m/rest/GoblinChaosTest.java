package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.Role;
import io.quarkiverse.goblin.AssaultEngine;
import io.quarkiverse.goblin.ChaosLayer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Goblin chaos extension against an inbound REST endpoint. Goblin ships disarmed
 * (quarkus.goblin.enabled=false) and never assaults the rest of the suite; this profile is the only place it is on.
 */
@QuarkusTest
@TestProfile(GoblinChaosTest.ChaosProfile.class)
@TestSecurity(user = "test-user", roles = {Role.USER_ROLE})
public class GoblinChaosTest {

    public static class ChaosProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.goblin.enabled", "true",
                    // the suite is never assaulted unless a test opts in
                    "quarkus.goblin.test.enabled", "true",
                    "quarkus.goblin.assault.type", "HTTP_STATUS",
                    "quarkus.goblin.assault.http-status.code", "503",
                    // assault every request, so the assertions are not a coin flip
                    "quarkus.goblin.target.level", "100");
        }
    }

    @Inject
    AssaultEngine goblin;

    @AfterEach
    public void rearm() {
        goblin.setActive(true);
    }

    @Test
    public void inbound_requests_are_assaulted_while_chaos_is_active() {
        assertTrue(goblin.isActive(), "chaos should be active: " + goblin.inactiveReason());
        assertTrue(goblin.isLayerAvailable(ChaosLayer.HTTP_IN), "the inbound HTTP layer should be armed");

        given()
                .when().get("/api/folder/count")
                .then()
                .statusCode(503);

        assertTrue(goblin.getTotalAssaultCount() > 0, "the engine should have recorded the assault");

        goblin.setActive(false);

        given()
                .when().get("/api/folder/count")
                .then()
                .statusCode(200);
    }
}
