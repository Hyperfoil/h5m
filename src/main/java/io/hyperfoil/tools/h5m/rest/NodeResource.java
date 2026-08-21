package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.EphemeralMode;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.Processing;
import io.hyperfoil.tools.h5m.api.node.NodeConfiguration;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ProcessingEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import io.hyperfoil.tools.h5m.entity.mapper.CycleAvoidingContext;
import io.hyperfoil.tools.h5m.svc.NodeService;
import io.hyperfoil.tools.h5m.svc.ValueService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UniqueElements;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Objects;

@Path("/api/node")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Node", description = "Manage transformation nodes in the DAG pipeline")
public class NodeResource {

    @Inject
    NodeServiceInterface nodeService;

    @Inject
    NodeService nodeServiceImpl; // for update() which is not on the interface

    @Inject
    ProcessingServiceInterface processingService;

    @Inject
    ValueService valueService;

    @Inject
    ApiMapper apiMapper;

    @POST
    @Authenticated
    @Operation(description = "Create a new node with an operation")
    public Node createNode(
            @QueryParam("name") @NotEmpty String name,
            @QueryParam("groupId") @NotNull Long groupId,
            @QueryParam("type") @NotNull NodeType type,
            @QueryParam("operation") @NotEmpty String operation) {
        return nodeService.create(name, groupId, type, operation);
    }

    @POST
    @Path("configured")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(description = "Create a new node with sources and configuration")
    public Node createConfigured(
            @QueryParam("name") @NotEmpty String name,
            @QueryParam("groupId") @NotNull Long groupId,
            @QueryParam("type") @NotNull NodeType type,
            @QueryParam("sources") @NotNull @NotEmpty @UniqueElements(message = "Duplicate source nodes are not allowed: each source node must serve a unique role") List<Long> sources,
            @RequestBody(required = false) @Valid NodeConfiguration configuration) {
        try {
            return nodeService.createConfigured(name, groupId, type, sources, configuration);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid node configuration request: " + e.getMessage(), e);
        }
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    @Transactional
    @Operation(description = "Update a node's name and/or operation. Absent params are left unchanged. An empty operation clears it. Triggers selective recalculation when the operation changes.")
    public Node update(@PathParam("id") Long id, @QueryParam("name") String name, @QueryParam("operation") String operation) {
        NodeEntity existing = NodeEntity.findById(id);
        if (existing == null) {
            throw new NotFoundException("Node not found: " + id);
        }
        if (name != null && name.isEmpty()) {
            throw new BadRequestException("Node name cannot be empty");
        }
        String newOperation = operation == null ? null : (operation.isEmpty() ? null : operation);
        boolean operationChanged = operation != null && !Objects.equals(existing.operation, newOperation);

        if (name != null) {
            existing.name = name;
        }
        if (operation != null) {
            existing.operation = newOperation;
        }
        nodeServiceImpl.update(existing);

        if (operationChanged && existing.group != null) {
            processingService.recalculateNode(id);
        }
        return apiMapper.toNode(existing, new CycleAvoidingContext());
    }

    @POST
    @Path("{id}/recalculate")
    @Authenticated
    @Operation(description = "Recalculate a specific node and its dependents. Returns immediately with a status for progress polling.")
    public Processing recalculateNode(@PathParam("id") Long nodeId) {
        return processingService.recalculateNode(nodeId);
    }

    @DELETE
    @Path("{id}")
    @Authenticated
    @Operation(description = "Delete a node by its ID")
    public void deleteNode(@PathParam("id") Long nodeId) {
        nodeService.delete(nodeId);
    }

    @PUT
    @Path("{id}/ephemeral")
    @Authenticated
    @Transactional
    @Operation(description = "Set ephemeral mode: DISCARD=discard data, KEEP=keep data, AUTO=system decides based on children")
    public void setEphemeral(
            @PathParam("id") Long nodeId,
            @QueryParam("mode") @Parameter(description = "DISCARD, KEEP, or AUTO") @DefaultValue("DISCARD") String mode) {
        NodeEntity node = NodeEntity.findById(nodeId);
        if (node == null) {
            throw new NotFoundException("Node not found: " + nodeId);
        }
        EphemeralMode ephemeralMode;
        try {
            ephemeralMode = EphemeralMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid mode: " + mode + ". Use DISCARD, KEEP, or AUTO");
        }
        if (ephemeralMode == EphemeralMode.DISCARD && (node.isDetection() || node.type() == NodeType.ROOT)) {
            throw new BadRequestException("Detection and root nodes cannot be set to ephemeral");
        }
        node.ephemeral = ephemeralMode;
        if (ephemeralMode == EphemeralMode.DISCARD) {
            // Only nullify existing data if no ingestion is in progress for this folder
            FolderEntity folder = FolderEntity.find("group.id", node.group.id).firstResult();
            long inFlight = folder != null
                    ? ProcessingEntity.count("valueId is not null and folderId = ?1 and completed = false", folder.id)
                    : 0;
            if (inFlight == 0) {
                valueService.nullifyNodeData(nodeId);
            }
            // If ingestion is in progress, data will be nullified when it completes
            // via nullifyEphemeralData() in the afterCleanup callback
        }
    }

    @GET
    @Path("find")
    @PermitAll
    @Operation(description = "Find nodes by FQDN within a specific group")
    public List<Node> findNodeByFqdn(
            @QueryParam("name") @Parameter(description = "FQDN of the node") String name,
            @QueryParam("groupId") @Parameter(description = "Group ID to search within") Long groupId) {
        return nodeService.findNodeByFqdn(name, groupId);
    }
}
