package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.AuthHeaderSecret;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.hyperfoil.tools.h5m.entity.NotificationChannelEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code updateChannel} is a partial update: a null field means "leave unchanged".
 * The method is immutable — it is derived from the config's discriminator, and changing it
 * would repoint the channel at a different plugin with incompatible stored config/secret.
 */
@QuarkusTest
public class NotificationChannelUpdateTest extends FreshDb {

    @Inject
    FolderService folderService;

    @Inject
    NotificationService notificationService;

    private NotificationChannel webhookChannel(long folderId, String name) {
        return notificationService.createChannel(folderId, name, NotificationMethod.WEBHOOK,
                WebhookConfig.of("https://hook.example.com"), AuthHeaderSecret.webHook("Bearer original"),
                "original template", true);
    }

    private NotificationChannel patch(long id, String name, NotificationConfiguration config,
                                      NotificationSecret secret, String template, Boolean enabled) {
        return notificationService.updateChannel(id,
                new NotificationChannel(null, name, null, null, null, config, secret, template, enabled));
    }

    @Test
    public void unknownChannelReturnsNull() {
        assertNull(patch(-1L, null, null, null, null, null));
    }

    @Test
    public void allNullFieldsLeaveTheChannelUntouched() {
        Folder folder = folderService.create("update_noop");
        NotificationChannel original = webhookChannel(folder.id(), "hook1");

        NotificationChannel updated = patch(original.id(), null, null, null, null, null);

        assertEquals("hook1", updated.name());
        assertEquals(NotificationMethod.WEBHOOK, updated.method());
        assertEquals("https://hook.example.com", ((WebhookConfig) updated.config()).url());
        assertEquals("original template", updated.template());
        assertTrue(updated.enabled());
    }

    @Test
    public void configIsReplaced() {
        Folder folder = folderService.create("update_config");
        NotificationChannel original = webhookChannel(folder.id(), "hook1");

        NotificationChannel updated = patch(original.id(), null,
                WebhookConfig.of("https://other.example.com"), null, null, null);

        assertEquals("https://other.example.com", ((WebhookConfig) updated.config()).url());
        // untouched fields survive
        assertEquals("hook1", updated.name());
        assertEquals("original template", updated.template());
    }

    @Test
    public void templateAndEnabledAreReplaced() {
        Folder folder = folderService.create("update_template");
        NotificationChannel original = webhookChannel(folder.id(), "hook1");

        NotificationChannel updated = patch(original.id(), null, null, null, "new template", false);

        assertEquals("new template", updated.template());
        assertFalse(updated.enabled());
    }

    @Test
    @Transactional
    public void secretIsStoredButNeverReturned() {
        Folder folder = folderService.create("update_secret");
        NotificationChannel original = webhookChannel(folder.id(), "hook1");

        NotificationChannel updated = patch(original.id(), null, null,
                AuthHeaderSecret.webHook("Bearer rotated"), null, null);

        assertNull(updated.secret(), "Secrets are write-only and must never be mapped back out");

        NotificationChannelEntity entity = NotificationChannelEntity.findById(original.id());
        assertEquals("Bearer rotated", ((AuthHeaderSecret) entity.getSecret()).authHeader());
    }

    @Test
    public void methodCannotBeChanged() {
        Folder folder = folderService.create("update_method");
        NotificationChannel original = webhookChannel(folder.id(), "hook1");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> patch(original.id(), null, SlackConfig.of("#alerts"), null, null, null));
        assertTrue(ex.getMessage().contains("cannot be changed"), ex.getMessage());

        // the rejected update leaves the stored config alone
        NotificationChannel unchanged = notificationService.findChannel(folder.id(), "hook1");
        assertEquals(NotificationMethod.WEBHOOK, unchanged.method());
        assertEquals("https://hook.example.com", ((WebhookConfig) unchanged.config()).url());
    }
}
