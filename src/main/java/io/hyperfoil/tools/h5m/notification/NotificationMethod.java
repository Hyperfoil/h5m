package io.hyperfoil.tools.h5m.notification;

/**
 * Supported notification methods.
 * Each value corresponds to a {@link NotificationPlugin} implementation.
 */
public enum NotificationMethod {
    WEBHOOK("webhook"),
    EMAIL("email"),
    SLACK("slack"),
    GITHUB_ISSUE("github-issue");

    private final String label;

    NotificationMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static NotificationMethod fromLabel(String label) {
        for (NotificationMethod m : values()) {
            if (m.label.equals(label)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown notification method: " + label);
    }
}
