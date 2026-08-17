package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.Processing;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/processing")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Processing", description = "Track processing status for uploads and recalculations")
public class ProcessingResource {

    @Inject
    ProcessingServiceInterface processingService;

    @GET
    @Path("upload/{id}")
    @PermitAll
    @Operation(description = "Get the processing status of an upload by root value ID.")
    public Processing getUploadStatus(@PathParam("id") long valueId) {
        Processing status = processingService.getIngestionStatus(valueId);
        if (status == null) {
            throw new NotFoundException("Upload not found: " + valueId);
        }
        return status;
    }

    @GET
    @Path("node/{id}")
    @PermitAll
    @Operation(description = "Get the processing status of a node recalculation.")
    public Processing getRecalculationStatus(@PathParam("id") long nodeId) {
        Processing status = processingService.getRecalculationStatus(nodeId);
        if (status == null) {
            throw new NotFoundException("Recalculation not found: " + nodeId);
        }
        return status;
    }

}
