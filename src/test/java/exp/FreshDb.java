package exp;

import io.agroal.api.AgroalDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class FreshDb {

    @Inject
    AgroalDataSource ds;

    @BeforeEach
    public void dropRows() throws SQLException {
        try(Connection conn = ds.getConnection()){
            conn.setAutoCommit(true);
            try(Statement stmt = conn.createStatement()){
                stmt.executeUpdate("DELETE from folder");
                stmt.executeUpdate("DELETE from nodegroup");
                stmt.executeUpdate("DELETE from node ");
                stmt.executeUpdate("DELETE from node_edge");
                stmt.executeUpdate("DELETE from value;");
                stmt.executeUpdate("DELETE from value_edge");
                stmt.executeUpdate("DELETE from work");
                stmt.executeUpdate("DELETE from work_nodes");
                stmt.executeUpdate("DELETE from work_values");
            }
        }
    }

}
