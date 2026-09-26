package io.hyperfoil.tools.h5m.api.notification;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Schema-only marker for a string holding an email address, referenced as the item type of email collections.
 */
@Schema(type = SchemaType.STRING, format = "email", description = "An email address")
public interface EmailAddress {
}
