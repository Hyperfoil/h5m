package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/group")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NodeGroup", description = "Manage node groups (transformation pipelines)")
public interface NodeGroupServiceInterface {

    @GET
    @Path("{name}")
    @Operation(description = "Retrieve a node group by its name")
    NodeGroup byName(@PathParam("name") String groupName);

    @DELETE
    @Path("{id}")
    @Operation(description = "Delete a node group by its ID")
    void delete(@PathParam("id") Long groupId);

}
