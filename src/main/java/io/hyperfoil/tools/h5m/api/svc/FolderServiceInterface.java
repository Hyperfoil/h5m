package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.yaup.json.Json;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/api/folder")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Folder", description = "Manage folders for uploaded data")
public interface FolderServiceInterface {

    @GET
    @Path("{name}")
    @Operation(description = "Retrieve a folder by its name")
    Folder byName(@PathParam("name") String name);

    @GET
    @Operation(description = "Get the upload count for all folders")
    Map<String, Integer> getFolderUploadCount();

    @POST
    @Path("{name}")
    @Operation(description = "Create a new folder")
    long create(@PathParam("name") String name);

    @DELETE
    @Path("{name}")
    @Operation(description = "Delete a folder by its name")
    long delete(@PathParam("name") String name);

    @POST
    @Path("{name}/upload")
    @Operation(description = "Upload JSON data to a folder")
    void upload(
            @PathParam("name") String name,
            @QueryParam("path") @Parameter(description = "Path within the folder") String path,
            JsonNode data);

    @POST
    @Path("{name}/recalculate")
    @Operation(description = "Recalculate all values in a folder")
    void recalculate(@PathParam("name") String name);

    @GET
    @Path("{name}/structure")
    @Operation(description = "Get the structural representation of a folder")
    Json structure(@PathParam("name") String name);

}
