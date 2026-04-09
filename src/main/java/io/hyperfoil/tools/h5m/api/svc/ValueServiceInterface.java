package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.api.Value;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/value")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Value", description = "Manage computed values produced by nodes")
public interface ValueServiceInterface {

    @DELETE
    @Operation(description = "Purge all values")
    void purgeValues();

    @GET
    @Path("node/{nodeId}/descendants")
    @Operation(description = "Get descendant values of a specific node")
    List<Value> getNodeDescendantValues(@PathParam("nodeId") Long nodeId);

    @GET
    @Path("node/{nodeId}/grouped")
    @Operation(description = "Get grouped values for a specific node")
    List<JsonNode> getGroupedValues(@PathParam("nodeId") Long nodeId);

}
