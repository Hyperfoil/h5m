package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.ApiKeyRequest;
import io.hyperfoil.tools.h5m.api.ApiKey;
import io.hyperfoil.tools.h5m.entity.ApiKeyEntity;
import io.hyperfoil.tools.h5m.svc.ApiKeyService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;

@Path("/api/apikey")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
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
    public ApiKey create(ApiKeyRequest request) {
        String username = identity.getPrincipal().getName();
        String description = request != null ? request.description() : null;
        String rawKey = apiKeyService.create(username, description);
        // Find the just-created key to return full metadata
        Instant now = Instant.now();
        return apiKeyService.listByUser(username).stream()
                .filter(k -> k.keyHash.equals(ApiKeyService.hashKey(rawKey)))
                .findFirst()
                .map(k -> toResponse(k, now, rawKey))
                .orElseThrow(() -> new InternalServerErrorException("Failed to retrieve created API key"));
    }

    @GET
    @Authenticated
    @Operation(description = "List all API keys for the authenticated user.")
    public List<ApiKey> list() {
        String username = identity.getPrincipal().getName();
        Instant now = Instant.now();
        return apiKeyService.listByUser(username).stream()
                .map(key -> toResponse(key, now))
                .toList();
    }

    @PUT
    @Path("{id}/revoke")
    @Authenticated
    @Operation(description = "Revoke an API key. The key must belong to the authenticated user, or the user must be an admin.")
    public void revoke(@PathParam("id") long keyId) {
        String username = identity.getPrincipal().getName();
        ApiKeyEntity key = ApiKeyEntity.findById(keyId);
        if (key == null) {
            throw new NotFoundException("API key not found: " + keyId);
        }
        // Ownership check: key must belong to caller, or caller must be admin
        if (!key.user.username.equals(username) && !identity.hasRole("admin")) {
            throw new ForbiddenException("Cannot revoke another user's API key");
        }
        apiKeyService.revoke(keyId);
    }

    private static ApiKey toResponse(ApiKeyEntity key, Instant now) {
        return toResponse(key, now, null);
    }

    private static ApiKey toResponse(ApiKeyEntity key, Instant now, String rawKey) {
        return new ApiKey(
                key.id,
                key.description,
                key.createdAt,
                key.lastUsedAt,
                key.revoked,
                key.isExpired(now),
                rawKey
        );
    }
}
