package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.svc.ValueService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/value")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Value", description = "Manage computed values produced by nodes")
public class ValueResource {

    @Inject
    ValueServiceInterface valueService;

    @Inject
    ValueService valueServiceImpl;

    @DELETE
    @RolesAllowed("admin")
    @Operation(description = "Purge all values")
    public void purgeValues() {
        valueService.purgeValues();
    }

    @GET
    @Path("{id}")
    @PermitAll
    @Operation(description = "Get a value's data by its ID")
    public JqValue getValueData(@PathParam("id") Long id) {
        JqValue data = valueServiceImpl.getValueData(id);
        if (data == null) {
            throw new NotFoundException("Value not found: " + id);
        }
        return data;
    }


    @GET
    @Path("{id}/descendants")
    @PermitAll
    @Operation(description = "Get all descendant values of a value. Use ?detection=true to filter to detection node values or ?node=id to filter to values from specified nodes. Detection is mutually exclusive and takes effect over node list")
    public List<Value> getDescendants(
            @PathParam("id") Long id,
            @QueryParam("detection") @DefaultValue("false") boolean detectionOnly,
            @QueryParam("node") List<Long> nodes) {
        if(valueServiceImpl.byId(id) == null){
            throw new  NotFoundException("Value not found: " + id);
        }
        if (detectionOnly) {
            return valueServiceImpl.getDetectionDescendants(id);
        }
        if (nodes != null && !nodes.isEmpty()) {
            return valueServiceImpl.getDescendantValues(id,nodes);
        }
        // General descendants — delegate to ValueService
        return valueServiceImpl.getAllDescendants(id);
    }

    @GET
    @Path("node/{nodeId}/descendants")
    @PermitAll
    @Operation(description = "Get descendant values of a specific node")
    public List<Value> getNodeDescendantValues(@PathParam("nodeId") Long nodeId) {
        return valueService.getNodeDescendantValues(nodeId);
    }

    @GET
    @Path("node/{nodeId}/grouped")
    @PermitAll
    @Operation(description = "Get grouped values for a specific node")
    public List<JqValue> getGroupedValues(@PathParam("nodeId") Long nodeId) {
        return valueService.getGroupedValues(nodeId);
    }

    @GET
    @Path("node/{nodeId}")
    @PermitAll
    @Operation(description = "Get all values produced by a specific node")
    public List<Value> getNodeValues(@PathParam("nodeId") Long nodeId) {
        return valueService.getNodeValues(nodeId);
    }
}
