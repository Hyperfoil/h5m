package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.ProcessingState;
import io.hyperfoil.tools.h5m.api.ProcessingStatus;
import io.hyperfoil.tools.h5m.svc.WorkService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

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
        ProcessingState state = workService.getProcessingStatus(id);
        return switch (state) {
            case PROCESSING -> new ProcessingStatus(id, ProcessingState.PROCESSING, null);
            case COMPLETED -> new ProcessingStatus(id, ProcessingState.COMPLETED, null);
            case FAILED -> new ProcessingStatus(id, ProcessingState.FAILED, "Processing failed");
            case NOT_FOUND -> throw new NotFoundException("Upload not found: " + id);
        };
    }

}
