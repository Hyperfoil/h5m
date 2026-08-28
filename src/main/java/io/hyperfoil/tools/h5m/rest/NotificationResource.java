package io.hyperfoil.tools.h5m.rest;

import io.hyperfoil.tools.h5m.api.Notification;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/notification")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Notification", description = "Manage notification channels and view sent notifications for change detection")
public class NotificationResource {

    @Inject
    NotificationServiceInterface notificationService;

    @POST
    @Path("channel")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Create a notification channel for a folder")
    public NotificationChannel createChannel(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId,
            @Valid NotificationChannel channel) {
        // The method is derived from the config's discriminator, not sent separately.
        return notificationService.createChannel(folderId, channel.name(), channel.config().method(),
                channel.config(), channel.secret(), channel.template(), channel.enabled());
    }

    @GET
    @Path("channel")
    @PermitAll
    @Operation(description = "List notification channels for a folder")
    public List<NotificationChannel> channels(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId) {
        return notificationService.channelsByFolder(folderId);
    }

    @PUT
    @Path("channel/{id}")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(description = "Update a notification channel")
    public NotificationChannel updateChannel(
            @PathParam("id") long id,
            @Valid NotificationChannel channel) {
        NotificationChannel updated = notificationService.updateChannel(id, channel);
        if (updated == null) {
            throw new NotFoundException("Notification channel not found: " + id);
        }
        return updated;
    }

    @DELETE
    @Path("channel/{id}")
    @Authenticated
    @Operation(description = "Delete a notification channel")
    public void deleteChannel(@PathParam("id") long id) {
        if (!notificationService.deleteChannel(id)) {
            throw new NotFoundException("Notification channel not found: " + id);
        }
    }

    @GET
    @PermitAll
    @Operation(description = "List sent notifications for a folder, most recent first")
    public List<Notification> sentNotifications(
            @QueryParam("folderId") @Parameter(description = "Folder ID") long folderId,
            @QueryParam("limit") @Parameter(description = "Max entries to return") @DefaultValue("50") int limit) {
        return notificationService.sentNotifications(folderId, limit);
    }
}
