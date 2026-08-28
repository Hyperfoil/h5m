package io.hyperfoil.tools.h5m.api;

/**
 * Supported notification methods.
 * Each value corresponds to a NotificationPlugin implementation.
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

}
