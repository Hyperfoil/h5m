package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.Role;
import io.hyperfoil.tools.h5m.api.Team;
import io.hyperfoil.tools.h5m.api.User;
import io.hyperfoil.tools.h5m.api.svc.TeamServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

import static io.hyperfoil.tools.h5m.api.Role.ADMIN_ROLE;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User", description = "User and role information")
public class UserResource {

    @Inject
    UserServiceInterface userService;

    @Inject
    TeamServiceInterface teamService;

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Operation(description = "List all users")
    public List<User> listUsers() {
        return userService.list();
    }

    @GET
    @Path("me")
    @Authenticated
    @Operation(description = "Get the authenticated user")
    public User currentUser() {
        User user = userService.resolveUser();
        if (user == null) {
            throw new InternalServerErrorException("The authenticated user is not present in the database");
        }
        return user;
    }

    @GET
    @Path("teams")
    @Authenticated
    @Operation(description = "Get the teams of the authenticated user")
    public List<Team> userTeams() {
        User user = userService.resolveUser();
        if (user == null) {
            throw new InternalServerErrorException("The authenticated user is not present in the database");
        }
        return teamService.listByUsername(user.username());
    }

    @GET
    @Path("administrators")
    @RolesAllowed(ADMIN_ROLE)
    @Operation(description = "List the users with the administrator role")
    public List<User> listAdministrators() {
        return administrators();
    }

    @PUT
    @Path("administrators/{userId}")
    @RolesAllowed(ADMIN_ROLE)
    @Operation(description = "Grant the administrator role to a user")
    public List<User> addAdministrator(@PathParam("userId") long userId) {
        setRole(userId, Role.ADMIN);
        return administrators();
    }

    @DELETE
    @Path("administrators/{userId}")
    @RolesAllowed(ADMIN_ROLE)
    @Operation(description = "Revoke the administrator role from a user")
    public List<User> removeAdministrator(@PathParam("userId") long userId) {
        User current = userService.resolveUser();
        if (current != null && current.id() == userId) {
            throw new BadRequestException("You cannot revoke your own administrator role");
        }
        setRole(userId, Role.USER);
        return administrators();
    }

    private void setRole(long userId, Role role) {
        if (userService.list().stream().noneMatch(u -> u.id() == userId)) {
            throw new NotFoundException("No user with id " + userId);
        }
        userService.setRole(userId, role);
    }

    private List<User> administrators() {
        return userService.list().stream().filter(u -> u.role() == Role.ADMIN).toList();
    }

}
