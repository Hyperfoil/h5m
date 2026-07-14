package io.hyperfoil.tools.h5m.provided;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DatabaseEngine {

    public enum Kind { SQLITE, POSTGRESQL }

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String jdbcUrl;

    public Kind kind() {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:") ? Kind.SQLITE : Kind.POSTGRESQL;
    }

    public boolean isSQLite() {
        return kind() == Kind.SQLITE;
    }
}
