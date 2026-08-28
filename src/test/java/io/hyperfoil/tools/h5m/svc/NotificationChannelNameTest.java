package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Channel names are addressable (channels are resolved by name within a folder),
 * so they must be renameable, never blank, and unique per folder.
 */
@QuarkusTest
public class NotificationChannelNameTest extends FreshDb {

    @Inject
    FolderService folderService;

    @Inject
    NotificationService notificationService;

    private NotificationChannel create(long folderId, String name) {
        return notificationService.createChannel(folderId, name, NotificationMethod.WEBHOOK,
                WebhookConfig.of("https://hook.example.com"), null, null, true);
    }

    private NotificationChannel rename(long id, String name) {
        return notificationService.updateChannel(id,
                new NotificationChannel(null, name, null, null, null, null, null, null, null));
    }

    @Test
    public void renameIsApplied() {
        Folder folder = folderService.create("rename_applied");
        NotificationChannel channel = create(folder.id(), "hook1");

        assertEquals("hook2", rename(channel.id(), "hook2").name());
        assertNotNull(notificationService.findChannelByName(folder.id(), "hook2"));
        assertNull(notificationService.findChannelByName(folder.id(), "hook1"));
    }

    @Test
    public void nullNameLeavesNameUnchanged() {
        Folder folder = folderService.create("rename_null");
        NotificationChannel channel = create(folder.id(), "hook1");

        assertEquals("hook1", rename(channel.id(), null).name());
    }

    @Test
    public void blankNameIsRejected() {
        Folder folder = folderService.create("rename_blank");
        NotificationChannel channel = create(folder.id(), "hook1");

        assertThrows(BadRequestException.class, () -> rename(channel.id(), "  "));
        assertEquals("hook1", notificationService.findChannel(folder.id(), "hook1").name());
    }

    @Test
    public void duplicateNameIsRejectedOnCreate() {
        Folder folder = folderService.create("create_duplicate");
        create(folder.id(), "hook1");

        assertThrows(BadRequestException.class, () -> create(folder.id(), "hook1"));
    }

    @Test
    public void duplicateNameIsRejectedOnRename() {
        Folder folder = folderService.create("rename_duplicate");
        create(folder.id(), "hook1");
        NotificationChannel second = create(folder.id(), "hook2");

        assertThrows(BadRequestException.class, () -> rename(second.id(), "hook1"));
        assertEquals("hook2", notificationService.findChannel(folder.id(), "hook2").name());
    }

    @Test
    public void renamingToItsOwnNameIsAllowed() {
        Folder folder = folderService.create("rename_self");
        NotificationChannel channel = create(folder.id(), "hook1");

        assertEquals("hook1", rename(channel.id(), "hook1").name());
    }

    @Test
    public void unknownFolderIsNotFound() {
        assertThrows(NotFoundException.class, () -> create(-1L, "hook1"));
    }

    @Test
    public void sameNameInAnotherFolderIsAllowed() {
        Folder first = folderService.create("scope_one");
        Folder second = folderService.create("scope_two");
        create(first.id(), "hook1");

        assertEquals("hook1", create(second.id(), "hook1").name());
    }
}
