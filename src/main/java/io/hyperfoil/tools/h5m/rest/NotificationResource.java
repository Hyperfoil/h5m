package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.NotificationConfigResponse;
import io.hyperfoil.tools.h5m.api.NotificationLogResponse;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.entity.NotificationLog;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.svc.NotificationService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/notification")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notification", description = "Manage notification configurations for change detection")
public class NotificationResource {

    @Inject
    NotificationService notificationService;

    @POST
    @Path("config")
    @Authenticated
    @Transactional
    @Operation(description = "Create a notification config for a folder")
    public long createConfig(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId,
            @QueryParam("method") @Parameter(description = "Notification method") NotificationMethod method,
            @QueryParam("secrets") @Parameter(description = "Secret config data (tokens, passwords)") String secrets,
            String data) {
        notificationService.validateConfig(method, data);
        FolderEntity folder = FolderEntity.findById(folderId);
        if (folder == null) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        NotificationConfig config = new NotificationConfig(folder, method, data, secrets);
        config.persist();
        return config.id;
    }

    @GET
    @Path("config")
    @PermitAll
    @Operation(description = "List notification configs for a folder")
    public List<NotificationConfigResponse> listConfigs(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId) {
        List<NotificationConfig> configs = NotificationConfig.find("folder.id", folderId).list();
        return configs.stream().map(c -> new NotificationConfigResponse(
                c.id, c.folder != null ? c.folder.id : null, c.method, c.data, c.template, c.enabled
        )).toList();
    }

    @PUT
    @Path("config/{id}")
    @Authenticated
    @Transactional
    @Operation(description = "Update a notification config")
    public void updateConfig(
            @PathParam("id") long id,
            @QueryParam("method") @Parameter(description = "New notification method") NotificationMethod method,
            @QueryParam("enabled") @Parameter(description = "Enable or disable") Boolean enabled,
            @QueryParam("secrets") @Parameter(description = "Secret config data (tokens, passwords)") String secrets,
            String data) {
        NotificationConfig config = NotificationConfig.findById(id);
        if (config == null) {
            throw new NotFoundException("Notification config not found: " + id);
        }
        if (method != null) {
            config.method = method;
        }
        if (data != null && !data.isBlank()) {
            notificationService.validateConfig(config.method, data);
            config.data = data;
        }
        if (secrets != null) {
            config.secrets = secrets;
        }
        if (enabled != null) {
            config.enabled = enabled;
        }
    }

    @DELETE
    @Path("config/{id}")
    @Authenticated
    @Transactional
    @Operation(description = "Delete a notification config")
    public void deleteConfig(@PathParam("id") long id) {
        boolean deleted = NotificationConfig.deleteById(id);
        if (!deleted) {
            throw new NotFoundException("Notification config not found: " + id);
        }
    }

    @GET
    @Path("log")
    @PermitAll
    @Operation(description = "Get notification log for a folder")
    public List<NotificationLogResponse> getLog(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId,
            @QueryParam("limit") @Parameter(description = "Max entries to return") @DefaultValue("50") int limit) {
        List<NotificationLog> logs = NotificationLog.find("folder.id = ?1 ORDER BY sentAt DESC", folderId)
            .page(0, limit)
            .list();
        return logs.stream().map(l -> new NotificationLogResponse(
                l.id, l.folder != null ? l.folder.id : null, l.method, l.destination,
                l.status, l.errorMessage, l.nodeId, l.nodeName, l.changeCount, l.sentAt
        )).toList();
    }
}
