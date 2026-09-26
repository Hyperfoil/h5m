package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Configuration for the Email notification plugin
 */
@Schema(description = "Configuration for the Email notification plugin")
public record EmailConfig(
        NotificationMethod method,
        @NotEmpty @Schema(type = SchemaType.ARRAY, implementation = EmailAddress.class) List<@NotNull @Email String> to,
        String subject) implements NotificationConfiguration {
    public EmailConfig {
        method = NotificationMethod.EMAIL;
    }

    public static EmailConfig of(List<String> to, String subject) {
        return new EmailConfig(null, to, subject);
    }
}
