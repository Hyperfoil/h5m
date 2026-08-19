package io.hyperfoil.tools.h5m.server;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;
import io.hyperfoil.tools.h5m.svc.SecurityEnabledProfile;
import io.hyperfoil.tools.h5m.svc.UserService;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(SecurityEnabledProfile.class)
public class AutoProvisioningTest extends FreshDb {

    private static final String TEST_ISS = "https://test-issuer";

    @Inject
    OidcUserProvisioner provisioner;

    @Inject
    UserService userService;

    private final AuthenticationRequestContext testContext = new AuthenticationRequestContext() {
        @Override
        public Uni<SecurityIdentity> runBlocking(Supplier<SecurityIdentity> supplier) {
            return Uni.createFrom().item(supplier);
        }
    };

    private SecurityIdentity identityFor(String username) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(username))
                .build();
    }

    private SecurityIdentity oidcIdentity(String sub, String username) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(new TestJwt(sub, TEST_ISS, username))
                .build();
    }

    @Test
    void first_user_gets_admin_role() {
        assertEquals(0, userService.count());
        provisioner.augment(identityFor("first-user"), testContext)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();

        User user = userService.byUsername("first-user");
        assertNotNull(user);
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    void subsequent_user_gets_user_role() {
        userService.create("existing-admin", Role.ADMIN);

        provisioner.augment(identityFor("new-user"), testContext)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();

        User user = userService.byUsername("new-user");
        assertNotNull(user);
        assertEquals(Role.USER, user.role());
    }

    @Test
    void existing_user_not_duplicated() {
        userService.create("alice", Role.USER);
        assertEquals(1, userService.count());

        provisioner.augment(identityFor("alice"), testContext)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();

        assertEquals(1, userService.count());
    }

    @Test
    void oidc_user_gets_provisioned_with_sub_and_iss() {
        userService.create("existing-admin", Role.ADMIN);

        provisioner.augment(oidcIdentity("sub-oidc", "oidc-user"), testContext)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem();

        User user = userService.bySub("sub-oidc", TEST_ISS);
        assertNotNull(user);
        assertEquals("oidc-user", user.username());
        assertEquals(Role.USER, user.role());
    }

    private record TestJwt(String sub, String iss, String preferredUsername) implements JsonWebToken {

        @Override
        public String getName() {
            return sub;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getClaim(String claimName) {
            return (T) switch (claimName) {
                case "sub" -> sub;
                case "iss" -> iss;
                case "preferred_username" -> preferredUsername;
                default -> null;
            };
        }

        @Override
        public Set<String> getClaimNames() {
            return Set.of("sub", "iss", "preferred_username");
        }
    }
}
