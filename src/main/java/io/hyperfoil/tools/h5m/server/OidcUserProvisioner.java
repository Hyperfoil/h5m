package io.hyperfoil.tools.h5m.server;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.svc.UserService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
@Priority(1)
public class OidcUserProvisioner implements SecurityIdentityAugmentor {

    @Inject
    UserService userService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> provisionIfNeeded(identity));
    }

    private SecurityIdentity provisionIfNeeded(SecurityIdentity identity) {
        return switch (identity.getPrincipal()) {
            case JsonWebToken jwt -> provisionOidc(identity, jwt);
            default -> provisionByUsername(identity);
        };
    }

    private SecurityIdentity provisionOidc(SecurityIdentity identity, JsonWebToken jwt) {
        String sub = jwt.getSubject();
        String iss = jwt.getIssuer();
        if (userService.bySub(sub, iss) != null) {
            return identity;
        }
        String username = jwt.getClaim("preferred_username");
        if (username == null) {
            username = jwt.getClaim("upn");
        }
        if (username == null) {
            username = sub;
        }
        Role role = userService.count() == 0 ? Role.ADMIN : Role.USER;
        userService.create(sub, iss, username, role);
        Log.infof("Auto-provisioned OIDC user %s with role %s", username, role);
        return identity;
    }

    private SecurityIdentity provisionByUsername(SecurityIdentity identity) {
        String username = identity.getPrincipal().getName();
        if (userService.byUsername(username) != null) {
            return identity;
        }
        Role role = userService.count() == 0 ? Role.ADMIN : Role.USER;
        userService.create(username, role);
        Log.infof("Auto-provisioned user %s with role %s", username, role);
        return identity;
    }
}
