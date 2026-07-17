package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.queue.UploadTracker;
import io.hyperfoil.tools.h5m.svc.WorkService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Optional;

@Path("/api/processing")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Processing", description = "Track upload processing status")
public class ProcessingResource {

    @Inject
    WorkService workService;

    @GET
    @Path("{id}")
    @PermitAll
    @Operation(description = "Get the processing status of an upload. Returns state (PROCESSING, COMPLETED, FAILED).")
    public ProcessingStatus getStatus(@PathParam("id") long id) {
        Optional<UploadTracker> tracker = workService.getTracker(id);
        if (tracker.isPresent()) {
            if (tracker.get().getFuture().isDone()) {
                if (tracker.get().getFuture().isCompletedExceptionally()) {
                    return new ProcessingStatus(id, ProcessingStatus.State.FAILED, "Processing failed");
                }
                return new ProcessingStatus(id, ProcessingStatus.State.COMPLETED, null);
            }
            return new ProcessingStatus(id, ProcessingStatus.State.PROCESSING, null);
        }
        // Tracker already cleaned up — check if root value exists in DB
        ValueEntity rootValue = ValueEntity.findById(id);
        if (rootValue != null && rootValue.node != null && rootValue.node.type() == NodeType.ROOT) {
            return new ProcessingStatus(id, ProcessingStatus.State.COMPLETED, null);
        }
        throw new NotFoundException("Upload not found: " + id);
    }

    /**
     * Processing status response for an upload operation.
     */
    public record ProcessingStatus(long id, State state, String error) {
        public enum State { PROCESSING, COMPLETED, FAILED }
    }
}
