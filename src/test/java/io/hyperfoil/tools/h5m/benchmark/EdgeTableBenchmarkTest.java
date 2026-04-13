package io.hyperfoil.tools.h5m.benchmark;

import io.agroal.api.AgroalDataSource;
import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.svc.FolderService;
import io.hyperfoil.tools.h5m.svc.NodeService;
import io.hyperfoil.tools.h5m.svc.ValueService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Database-backed edge table benchmarks.
 * Measures insertion, query, and edge-growth scaling at various DAG topologies and sizes.
 * The node_edge and value_edge tables are adjacency list join tables (direct parent-child edges);
 * transitive relationships are computed at query time via recursive SQL CTEs.
 * <p>
 * Run with: mvn test -Dtest=EdgeTableBenchmarkTest
 * PostgreSQL: mvn test -Dtest=EdgeTableBenchmarkTest -Dquarkus.datasource.db-kind=postgresql
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EdgeTableBenchmarkTest extends FreshDb {

    private static final int WARMUP = 2;
    private static final int MEASURE = 5;
    private static final List<BenchmarkTimer.Result> results = new ArrayList<>();
    private static final List<String> edgeReports = new ArrayList<>();

    @Inject
    NodeService nodeService;

    @Inject
    FolderService folderService;

    @Inject
    ValueService valueService;

    @Inject
    TransactionManager tm;

    @Inject
    AgroalDataSource dataSource;

    private DbGraphBuilder graphBuilder;

    @BeforeEach
    void initBuilder() throws Exception {
        // Roll back any stale transaction from a prior failed test
        if (tm.getTransaction() != null) {
            tm.rollback();
        }
        graphBuilder = new DbGraphBuilder(nodeService, tm);
    }

    // ==================== Flat topology insertion ====================

    @Test @Order(1)
    void insertFlat_100() throws Exception {
        benchInsertFlat(100);
    }

    @Test @Order(2)
    void insertFlat_500() throws Exception {
        benchInsertFlat(500);
    }

    @Test @Order(3)
    void insertFlat_1000() throws Exception {
        benchInsertFlat(1000);
    }

    @Test @Order(4)
    void insertFlat_2000() throws Exception {
        benchInsertFlat(2000);
    }

    // ==================== Chain topology insertion ====================

    @Test @Order(10)
    void insertChain_100() throws Exception {
        benchInsertChain(100);
    }

    @Test @Order(11)
    void insertChain_500() throws Exception {
        benchInsertChain(500);
    }

    @Test @Order(12)
    void insertChain_1000() throws Exception {
        benchInsertChain(1000);
    }

    // ==================== Diamond topology insertion ====================

    @Test @Order(20)
    void insertDiamond_3x5() throws Exception {
        benchInsertDiamond(3, 5);
    }

    @Test @Order(21)
    void insertDiamond_5x10() throws Exception {
        benchInsertDiamond(5, 10);
    }

    @Test @Order(22)
    void insertDiamond_8x20() throws Exception {
        benchInsertDiamond(8, 20);
    }

    // ==================== Query: findNodeByFqdn ====================

    @Test @Order(30)
    void queryFqdn_100() throws Exception {
        benchQueryFqdn(100);
    }

    @Test @Order(31)
    void queryFqdn_500() throws Exception {
        benchQueryFqdn(500);
    }

    @Test @Order(32)
    void queryFqdn_1000() throws Exception {
        benchQueryFqdn(1000);
    }

    // ==================== Query: getNodeDescendantValues ====================

    @Test @Order(40)
    void queryDescendants_chain_100() throws Exception {
        benchQueryDescendants("chain", 100);
    }

    @Test @Order(41)
    void queryDescendants_chain_500() throws Exception {
        benchQueryDescendants("chain", 500);
    }

    @Test @Order(42)
    void queryDescendants_diamond_5x10() throws Exception {
        benchQueryDescendantsDiamond(5, 10);
    }

    // ==================== Final report ====================

    @AfterAll
    static void printReport() {
        System.out.println("\n========== EDGE TABLE BENCHMARK RESULTS ==========");
        System.out.println(BenchmarkTimer.csvHeader());
        for (BenchmarkTimer.Result r : results) {
            System.out.println(BenchmarkTimer.toCsv(r));
        }

        System.out.println("\n========== EDGE COUNT REPORTS ==========");
        for (String report : edgeReports) {
            System.out.println(report);
        }
        System.out.println("=====================================================\n");
    }

    // ==================== Benchmark helpers ====================

    private void benchInsertFlat(int count) throws Exception {
        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "insert_flat_" + count, WARMUP, MEASURE,
                this::cleanDb,
                () -> graphBuilder.buildFlat("flat_group", count)
        );
        // Run one more time to capture edge counts
        cleanDb();
        graphBuilder.buildFlat("flat_group", count);
        reportEdgeCounts("flat_" + count);
        results.add(result);
    }

    private void benchInsertChain(int depth) throws Exception {
        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "insert_chain_" + depth, WARMUP, MEASURE,
                this::cleanDb,
                () -> graphBuilder.buildChain("chain_group", depth)
        );
        cleanDb();
        graphBuilder.buildChain("chain_group", depth);
        reportEdgeCounts("chain_" + depth);
        results.add(result);
    }

    private void benchInsertDiamond(int layers, int width) throws Exception {
        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "insert_diamond_" + layers + "x" + width, WARMUP, MEASURE,
                this::cleanDb,
                () -> graphBuilder.buildDiamond("diamond_group", layers, width)
        );
        cleanDb();
        graphBuilder.buildDiamond("diamond_group", layers, width);
        reportEdgeCounts("diamond_" + layers + "x" + width);
        results.add(result);
    }

    private void benchQueryFqdn(int groupSize) throws Exception {
        // Setup: build a flat topology once
        cleanDb();
        graphBuilder.buildFlat("fqdn_group", groupSize);

        // Benchmark: find the last node by FQDN
        String targetName = "n_" + (groupSize - 1);
        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "query_fqdn_" + groupSize, WARMUP, MEASURE,
                () -> {},
                () -> nodeService.findNodeByFqdn(targetName)
        );
        results.add(result);
    }

    private void benchQueryDescendants(String topology, int size) throws Exception {
        cleanDb();
        long groupId;
        if ("chain".equals(topology)) {
            groupId = graphBuilder.buildChain("desc_group", size);
        } else {
            groupId = graphBuilder.buildFlat("desc_group", size);
        }

        // Upload a simple JSON document to create values
        tm.begin();
        folderService.create("bench_folder");
        tm.commit();
        tm.begin();
        folderService.upload("bench_folder", "$.data",
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"data\": 42}"));
        tm.commit();

        // Get the root node ID for querying
        tm.begin();
        var group = io.hyperfoil.tools.h5m.entity.NodeGroupEntity.<io.hyperfoil.tools.h5m.entity.NodeGroupEntity>findById(groupId);
        long rootNodeId = group.root.id;
        tm.commit();

        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "query_descendants_" + topology + "_" + size, WARMUP, MEASURE,
                () -> {},
                () -> valueService.getNodeDescendantValues(rootNodeId)
        );
        results.add(result);
    }

    private void benchQueryDescendantsDiamond(int layers, int width) throws Exception {
        cleanDb();
        long groupId = graphBuilder.buildDiamond("desc_diamond", layers, width);

        tm.begin();
        folderService.create("bench_folder");
        tm.commit();
        tm.begin();
        folderService.upload("bench_folder", "$.data",
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"data\": 42}"));
        tm.commit();

        tm.begin();
        var group = io.hyperfoil.tools.h5m.entity.NodeGroupEntity.<io.hyperfoil.tools.h5m.entity.NodeGroupEntity>findById(groupId);
        long rootNodeId = group.root.id;
        tm.commit();

        BenchmarkTimer.Result result = BenchmarkTimer.run(
                "query_descendants_diamond_" + layers + "x" + width, WARMUP, MEASURE,
                () -> {},
                () -> valueService.getNodeDescendantValues(rootNodeId)
        );
        results.add(result);
    }

    private void cleanDb() throws Exception {
        // Roll back any stale transaction left from a failed iteration
        if (tm.getTransaction() != null) {
            tm.rollback();
        }
        dropRows();
    }

    private void reportEdgeCounts(String label) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            long nodes = countRows(stmt, "node");
            long nodeEdges = countRows(stmt, "node_edge");
            long valueEdges = countRows(stmt, "value_edge");
            String report = String.format("[EDGES] %s: nodes=%d, node_edges=%d, ratio=%.2f, value_edges=%d",
                    label, nodes, nodeEdges, (double) nodeEdges / Math.max(nodes, 1), valueEdges);
            edgeReports.add(report);
        }
    }

    private long countRows(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return ((Number) rs.getObject(1)).longValue();
        }
    }
}
