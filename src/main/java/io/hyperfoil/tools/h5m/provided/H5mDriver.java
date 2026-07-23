package io.hyperfoil.tools.h5m.provided;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC driver wrapper that delegates to SQLite or PostgreSQL based on the URL prefix.
 * Configured as {@code quarkus.datasource.jdbc.driver} so the Quarkus-managed datasource
 * can handle both database types at runtime.
 */
public class H5mDriver implements Driver {

    private static final Driver SQLITE = new org.sqlite.JDBC();
    private static final Driver POSTGRESQL = new org.postgresql.Driver();

    private Driver delegate(String url) {
        if (url != null && url.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        }
        return POSTGRESQL;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate(url).connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return SQLITE.acceptsURL(url) || POSTGRESQL.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate(url).getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getLogger("io.hyperfoil.tools.h5m");
    }
}
