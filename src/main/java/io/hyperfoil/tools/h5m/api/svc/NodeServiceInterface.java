package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/node")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Node", description = "Manage transformation nodes in the DAG pipeline")
public interface NodeServiceInterface {

    @POST
    @Operation(description = "Create a new node with an operation")
    Long create(
            @QueryParam("name") @NotEmpty String name,
            @QueryParam("groupId") @NotNull Long groupId,
            @QueryParam("type") @NotNull NodeType type,
            @QueryParam("operation") String operation);

    @POST
    @Path("configured")
    @Operation(description = "Create a new node with sources and configuration")
    Long createConfigured(
            @QueryParam("name") @NotEmpty String name,
            @QueryParam("groupId") @NotNull Long groupId,
            @QueryParam("type") @NotNull NodeType type,
            @QueryParam("sources") List<Long> sources,
            Object configuration) throws JsonProcessingException;

    @DELETE
    @Path("{id}")
    @Operation(description = "Delete a node by its ID")
    void delete(@PathParam("id") Long nodeId);

    @GET
    @Path("find")
    @Operation(description = "Find nodes by FQDN within a specific group")
    List<Node> findNodeByFqdn(
            @QueryParam("name") @Parameter(description = "FQDN of the node") String name,
            @QueryParam("groupId") @Parameter(description = "Group ID to search within") Long groupId);

    /**
     * Find nodes by fully qualified domain name (without group context).
     * Used by CLI commands. Not exposed as a REST endpoint.
     */
    List<Node> findNodeByFqdn(String fqdn);

}
