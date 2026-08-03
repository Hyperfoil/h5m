package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.ApiKey;
import io.hyperfoil.tools.h5m.svc.ApiKeyService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/apikey")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "API Key", description = "Manage API keys for authentication")
public class ApiKeyResource {

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    SecurityIdentity identity;

    @POST
    @Authenticated
    @Operation(description = "Create a new API key for the authenticated user. " +
            "The response includes the raw key in the 'rawKey' field — this is the only time it is displayed.")
    @APIResponse(responseCode = "200", description = "API key created",
            content = @Content(schema = @Schema(implementation = ApiKey.class)))
    public ApiKey create(
            @QueryParam("description") @NotEmpty @Parameter(description = "Human-readable label for the key") String description) {
        return apiKeyService.create(identity.getPrincipal().getName(), description);
    }

    @GET
    @Authenticated
    @Operation(description = "List all API keys for the authenticated user.")
    @APIResponse(responseCode = "200", description = "List of API keys",
            content = @Content(schema = @Schema(implementation = ApiKey.class)))
    public List<ApiKey> list() {
        return apiKeyService.listByUser(identity.getPrincipal().getName());
    }

    @PUT
    @Path("{id}/revoke")
    @Authenticated
    @Operation(description = "Revoke an API key. The key must belong to the authenticated user, or the user must be an admin.")
    @APIResponse(responseCode = "204", description = "API key revoked")
    @APIResponse(responseCode = "403", description = "Cannot revoke another user's key")
    @APIResponse(responseCode = "404", description = "API key not found")
    public void revoke(@PathParam("id") long keyId) {
        ApiKey key = apiKeyService.getById(keyId);
        if (key == null) {
            throw new NotFoundException("API key not found: " + keyId);
        }
        String username = identity.getPrincipal().getName();
        if (!key.owner().equals(username) && !identity.hasRole("admin")) {
            throw new ForbiddenException("Cannot revoke another user's API key");
        }
        apiKeyService.revoke(keyId);
    }
}
