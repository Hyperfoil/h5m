package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.User;
import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User", description = "User and role information")
public class UserResource {

    @Inject
    UserServiceInterface userService;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("role")
    @Authenticated
    @Operation(description = "Get the role of the authenticated user")
    @APIResponse(responseCode = "200", description = "User role")
    public Role role() {
        User user = switch (identity.getPrincipal()) {
            case JsonWebToken jwt -> userService.bySub(jwt.getSubject(), jwt.getIssuer());
            default -> userService.byUsername(identity.getPrincipal().getName());
        };
        return user != null ? user.role() : Role.USER;
    }
}
