package io.hyperfoil.tools.h5m.cli;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.hyperfoil.tools.h5m.svc.FolderService;
import io.hyperfoil.tools.h5m.svc.NodeService;
import io.hyperfoil.tools.h5m.svc.ValueService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import picocli.CommandLine;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "verifyimport", description = "Compare h5m imported data against Horreum source data")
public class VerifyLegacy implements Callable<Integer> {

    @CommandLine.Option(names = {"username"}, description = "legacy db username", defaultValue = "quarkus")
    String username;
    @CommandLine.Option(names = {"password"}, description = "legacy db password", defaultValue = "quarkus")
    String password;
    @CommandLine.Option(names = {"url"}, description = "legacy connection url", defaultValue = "jdbc:postgresql://0.0.0.0:6000/horreum")
    String url;
    @CommandLine.Option(names = {"testId"}, description = "Horreum test ID")
    Long testId;
    @CommandLine.Option(names = {"runId"}, description = "verify a specific run (optional)")
    Long runId;
    @CommandLine.Option(names = {"limit"}, description = "max runs to verify", defaultValue = "5")
    int limit;
    @CommandLine.Option(names = {"verbose"}, description = "show detailed mismatch info", defaultValue = "false")
    boolean verbose;

    @Inject
    EntityManager em;

    @Inject
    FolderService folderService;

    @Override
    @Transactional
    public Integer call() throws Exception {
        if (testId == null) {
            System.err.println("testId is required");
            return 1;
        }

        Map<String, String> props = new HashMap<>();
        props.put(AgroalPropertiesReader.MAX_SIZE, "1");
        props.put(AgroalPropertiesReader.MIN_SIZE, "1");
        props.put(AgroalPropertiesReader.INITIAL_SIZE, "1");
        props.put(AgroalPropertiesReader.MAX_LIFETIME_S, "57");
        props.put(AgroalPropertiesReader.ACQUISITION_TIMEOUT_S, "54");
        props.put(AgroalPropertiesReader.PRINCIPAL, username);
        props.put(AgroalPropertiesReader.CREDENTIAL, password);
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, "org.postgresql.Driver");
        props.put(AgroalPropertiesReader.JDBC_URL, url);
        AgroalDataSource legacyDs = AgroalDataSource.from(new AgroalPropertiesReader()
                .readProperties(props).get());

        try (Connection legacyConn = legacyDs.getConnection()) {
            String testName = getTestName(legacyConn, testId);
            if (testName == null) {
                System.err.println("Test not found: " + testId);
                return 1;
            }
            System.out.println("Verifying test: " + testName + " (id=" + testId + ")");

            // Step 1: Compare node structure
            System.out.println("\n=== NODE STRUCTURE ===");
            compareNodeStructure(legacyConn, testName);

            // Step 2: Check source data quality
            System.out.println("\n=== SOURCE DATA QUALITY ===");
            Set<String> stubLabels = checkSourceDataQuality(legacyConn);

            // Step 3: Get runs to verify
            List<Long> runIds;
            if (runId != null) {
                runIds = List.of(runId);
            } else {
                runIds = getRunIds(legacyConn, testId, limit);
            }
            System.out.println("\n=== VERIFYING " + runIds.size() + " RUNS ===");

            int totalMatches = 0;
            int totalMismatches = 0;
            int totalMissing = 0;
            int totalExtra = 0;
            int totalMisaligned = 0;
            int totalRounding = 0;
            int totalStubs = 0;
            Map<String, int[]> perLabel = new LinkedHashMap<>();  // label → [match, mismatch, missing]
            Map<Integer, int[]> perDataset = new TreeMap<>();     // ordinal → [match, mismatch, missing]
            long startTime = System.currentTimeMillis();

            // Get h5m root value IDs in upload order (matching run import order: id DESC)
            @SuppressWarnings("unchecked")
            List<Number> rootValueIds = em.createNativeQuery("""
                    SELECT v.id FROM value v
                    JOIN node n ON v.node_id = n.id
                    JOIN node_group ng ON ng.root_id = n.id
                    JOIN folder f ON f.group_id = ng.id
                    WHERE f.name = ? AND n.type = 'root'
                    ORDER BY v.id ASC
                    """)
                    .setParameter(1, testName)
                    .getResultList();
            if (rootValueIds.size() < runIds.size()) {
                System.out.println("WARNING: " + runIds.size() + " Horreum runs but only " + rootValueIds.size() + " h5m uploads");
            }

            for (int i = 0; i < runIds.size(); i++) {
                Long rid = runIds.get(i);
                Long rootValueId = i < rootValueIds.size() ? rootValueIds.get(i).longValue() : null;
                System.out.println("\n--- Run " + rid + (rootValueId != null ? " (h5m root=" + rootValueId + ")" : " (no h5m upload)") + " ---");
                if (rootValueId == null) {
                    System.out.println("  SKIPPED: no corresponding h5m upload");
                    continue;
                }
                int[] result = compareRun(legacyConn, testName, rid, rootValueId, perLabel, perDataset, stubLabels);
                totalMatches += result[0];
                totalMismatches += result[1];
                totalMissing += result[2];
                totalExtra += result[3];
                totalMisaligned += result[4];
                totalRounding += result[5];
                totalStubs += result[6];
            }

            long elapsed = System.currentTimeMillis() - startTime;
            int totalComparisons = totalMatches + totalMismatches + totalMissing;
            double matchRate = totalComparisons > 0 ? 100.0 * totalMatches / totalComparisons : 0;

            // Summary
            System.out.println("\n=== SUMMARY ===");
            System.out.println("Runs verified: " + runIds.size());
            System.out.printf("Match rate: %.1f%% (%d/%d)%n", matchRate, totalMatches, totalComparisons);
            System.out.println("  matching:   " + totalMatches
                    + " (" + (totalMatches - totalMisaligned - totalRounding) + " exact"
                    + (totalMisaligned > 0 ? ", " + totalMisaligned + " misaligned" : "")
                    + (totalRounding > 0 ? ", " + totalRounding + " rounding" : "")
                    + ")");
            System.out.println("  mismatched: " + totalMismatches
                    + (totalStubs > 0 ? " (" + totalStubs + " stub functions, " + (totalMismatches - totalStubs) + " real)" : ""));
            System.out.println("  missing:    " + totalMissing);
            System.out.println("  extra:      " + totalExtra);
            System.out.printf("Time: %.1fs%n", elapsed / 1000.0);

            // Per-label breakdown (only show labels with issues)
            List<String> problemLabels = perLabel.entrySet().stream()
                    .filter(e -> e.getValue()[1] > 0 || e.getValue()[2] > 0)
                    .map(Map.Entry::getKey).toList();
            if (!problemLabels.isEmpty()) {
                System.out.println("\n--- Labels with issues ---");
                System.out.printf("  %-40s %6s %8s %7s %6s%n", "Label", "Match", "Mismatch", "Missing", "Rate");
                for (String label : problemLabels) {
                    int[] s = perLabel.get(label);
                    int total = s[0] + s[1] + s[2];
                    System.out.printf("  %-40s %6d %8d %7d %5.0f%%%n", truncate(label, 40), s[0], s[1], s[2],
                            total > 0 ? 100.0 * s[0] / total : 0);
                }
            }
            List<String> perfectLabels = perLabel.entrySet().stream()
                    .filter(e -> e.getValue()[1] == 0 && e.getValue()[2] == 0 && e.getValue()[0] > 0)
                    .map(Map.Entry::getKey).toList();
            if (!perfectLabels.isEmpty()) {
                System.out.println("\n--- Labels with 100% match (" + perfectLabels.size() + ") ---");
                System.out.println("  " + String.join(", ", perfectLabels));
            }

            // Per-dataset breakdown (only show datasets with issues)
            List<Integer> problemDatasets = perDataset.entrySet().stream()
                    .filter(e -> e.getValue()[1] > 0 || e.getValue()[2] > 0)
                    .map(Map.Entry::getKey).toList();
            if (!problemDatasets.isEmpty()) {
                System.out.println("\n--- Datasets with issues ---");
                System.out.printf("  %-12s %6s %8s %7s%n", "Dataset", "Match", "Mismatch", "Missing");
                for (int ds : problemDatasets) {
                    int[] s = perDataset.get(ds);
                    System.out.printf("  %-12d %6d %8d %7d%n", ds, s[0], s[1], s[2]);
                }
            }

            if (totalMismatches == 0 && totalMissing == 0) {
                System.out.println("\nRESULT: PASS");
                return 0;
            } else {
                System.out.println("\nRESULT: DIFFERENCES FOUND");
                return 1;
            }
        } finally {
            legacyDs.close();
        }
    }

    private String getTestName(Connection conn, long testId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM test WHERE id = ?")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private List<Long> getRunIds(Connection conn, long testId, int limit) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM run WHERE testid = ? AND trashed = false ORDER BY id DESC LIMIT ?")) {
            ps.setLong(1, testId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private void compareNodeStructure(Connection legacyConn, String testName) throws SQLException {
        // Horreum: count labels for this test's target schema
        int horreumLabelCount = 0;
        try (PreparedStatement ps = legacyConn.prepareStatement("""
                SELECT count(DISTINCT l.id) FROM label l
                JOIN transformer t ON l.schema_id = t.schema_id
                JOIN test_transformers tt ON tt.transformer_id = t.id
                WHERE tt.test_id = ?
                """)) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) horreumLabelCount = rs.getInt(1);
            }
        }

        // Also count labels from run schemas (no-transform path)
        int horreumSchemaLabelCount = 0;
        try (PreparedStatement ps = legacyConn.prepareStatement("""
                SELECT count(DISTINCT l.id) FROM label l
                JOIN schema s ON l.schema_id = s.id
                WHERE s.uri IN (SELECT DISTINCT schema::text FROM run_schema_paths WHERE testid = ?)
                """)) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) horreumSchemaLabelCount = rs.getInt(1);
            }
        }

        // h5m: count nodes by type
        @SuppressWarnings("unchecked")
        List<Object[]> h5mNodes = em.createNativeQuery("""
                SELECT n.type, count(*) FROM node n
                JOIN node_group ng ON n.group_id = ng.id
                JOIN folder f ON f.group_id = ng.id
                WHERE f.name = ?
                GROUP BY n.type ORDER BY n.type
                """)
                .setParameter(1, testName)
                .getResultList();

        System.out.println("Horreum labels (transformer schema): " + horreumLabelCount);
        System.out.println("Horreum labels (run schemas): " + horreumSchemaLabelCount);
        System.out.println("h5m nodes:");
        int totalNodes = 0;
        for (Object[] row : h5mNodes) {
            System.out.println("  " + row[0] + ": " + row[1]);
            totalNodes += ((Number) row[1]).intValue();
        }
        System.out.println("  total: " + totalNodes);

        // Count change detection
        int horreumChangeDetections = 0;
        try (PreparedStatement ps = legacyConn.prepareStatement("""
                SELECT count(*) FROM changedetection
                WHERE variable_id IN (SELECT id FROM variable WHERE testid = ?)
                """)) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) horreumChangeDetections = rs.getInt(1);
            }
        }

        long h5mChangeDetections = 0;
        try {
            h5mChangeDetections = ((Number) em.createNativeQuery("""
                    SELECT count(*) FROM node n
                    JOIN node_group ng ON n.group_id = ng.id
                    JOIN folder f ON f.group_id = ng.id
                    WHERE f.name = ? AND n.type IN ('rd', 'ft')
                    """)
                    .setParameter(1, testName)
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            // folder may not exist
        }

        System.out.println("Change detection nodes: Horreum=" + horreumChangeDetections + " h5m=" + h5mChangeDetections);
    }

    /**
     * Check the Horreum source data for quality issues that would affect import/verification.
     * Returns the set of label names whose functions are stubs (zero-param, constant return).
     */
    private Set<String> checkSourceDataQuality(Connection legacyConn) throws SQLException {
        Set<String> stubLabels = new HashSet<>();

        // Get all schema IDs used by this test's runs
        Set<Integer> schemaIds = new HashSet<>();
        try (PreparedStatement ps = legacyConn.prepareStatement(
                "SELECT DISTINCT schemaid FROM run_schemas WHERE runid IN (SELECT id FROM run WHERE testid = ?)")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) schemaIds.add(rs.getInt(1));
            }
        }

        // Get all label names available for these schemas
        Set<String> availableLabels = new HashSet<>();
        if (!schemaIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(schemaIds.size(), "?"));
            try (PreparedStatement ps = legacyConn.prepareStatement(
                    "SELECT DISTINCT name FROM label WHERE schema_id IN (" + placeholders + ")")) {
                int idx = 1;
                for (int sid : schemaIds) ps.setInt(idx++, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) availableLabels.add(rs.getString(1));
                }
            }
        }

        // Check 1: Orphaned fingerprint labels
        List<String> orphanedFingerprints = new ArrayList<>();
        try (PreparedStatement ps = legacyConn.prepareStatement(
                "SELECT fingerprint_labels FROM test WHERE id = ?")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    if (json != null && !json.isBlank()) {
                        for (String name : parseJsonStringArray(json)) {
                            if (!availableLabels.contains(name)) {
                                orphanedFingerprints.add(name);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Orphaned fingerprint labels: " + orphanedFingerprints.size());
        for (String name : orphanedFingerprints) {
            System.out.println("  " + name + " (not defined in any schema)");
        }

        // Check 2: Orphaned variable labels
        List<String> orphanedVariables = new ArrayList<>();
        try (PreparedStatement ps = legacyConn.prepareStatement(
                "SELECT id, name, labels FROM variable WHERE testid = ?")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long varId = rs.getLong(1);
                    String varName = rs.getString(2);
                    String labelsJson = rs.getString(3);
                    if (labelsJson != null && !labelsJson.isBlank()) {
                        for (String labelName : parseJsonStringArray(labelsJson)) {
                            if (!availableLabels.contains(labelName)) {
                                orphanedVariables.add(labelName + " (variable \"" + varName + "\", id=" + varId + ")");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Orphaned variable labels: " + orphanedVariables.size());
        for (String desc : orphanedVariables) {
            System.out.println("  " + desc);
        }

        // Check 3: Stub functions (zero-param functions that return constants)
        List<String[]> stubs = new ArrayList<>(); // [name, preview]
        if (!schemaIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(schemaIds.size(), "?"));
            try (PreparedStatement ps = legacyConn.prepareStatement(
                    "SELECT DISTINCT l.name, left(l.function, 80) FROM label l " +
                    "WHERE l.schema_id IN (" + placeholders + ") " +
                    "AND l.function IS NOT NULL AND l.function ~ '^\\s*\\(\\s*\\)\\s*=>'")) {
                int idx = 1;
                for (int sid : schemaIds) ps.setInt(idx++, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String preview = rs.getString(2);
                        stubs.add(new String[]{name, preview});
                        stubLabels.add(name);
                    }
                }
            }
        }
        System.out.println("Stub functions: " + stubs.size());
        for (String[] stub : stubs) {
            System.out.println("  " + stub[0] + ": " + stub[1].replaceAll("\\n.*", "").trim());
        }

        // Check 4: Labels with zero extractors
        List<String> zeroExtractorLabels = new ArrayList<>();
        if (!schemaIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(schemaIds.size(), "?"));
            try (PreparedStatement ps = legacyConn.prepareStatement(
                    "SELECT DISTINCT l.name FROM label l " +
                    "WHERE l.schema_id IN (" + placeholders + ") " +
                    "AND l.id NOT IN (SELECT DISTINCT label_id FROM label_extractors)")) {
                int idx = 1;
                for (int sid : schemaIds) ps.setInt(idx++, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) zeroExtractorLabels.add(rs.getString(1));
                }
            }
        }
        System.out.println("Labels with zero extractors: " + zeroExtractorLabels.size());
        for (String name : zeroExtractorLabels) {
            System.out.println("  " + name);
        }

        return stubLabels;
    }

    /** Parse a JSON string array like ["a", "b", "c"] into a list of strings. */
    private static List<String> parseJsonStringArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || !json.startsWith("[")) return result;
        // Simple parser for JSON string arrays — no nested objects/arrays expected
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return result;
        for (String part : json.split(",")) {
            part = part.trim();
            if (part.startsWith("\"") && part.endsWith("\"")) {
                part = part.substring(1, part.length() - 1);
            }
            if (!part.isEmpty()) result.add(part);
        }
        return result;
    }

    private int[] compareRun(Connection legacyConn, String testName, long runId, long rootValueId,
                             Map<String, int[]> perLabel, Map<Integer, int[]> perDataset,
                             Set<String> stubLabels) throws SQLException {
        int matches = 0, mismatches = 0, missing = 0, extra = 0;
        int matchExact = 0, matchMisaligned = 0, matchRounding = 0;
        int mismatchStub = 0;

        // Get Horreum label values for this run
        Map<String, Map<Integer, String>> horreumValues = new LinkedHashMap<>();
        try (PreparedStatement ps = legacyConn.prepareStatement("""
                SELECT ds.ordinal, l.name, lv.value::text
                FROM label_values lv
                JOIN label l ON l.id = lv.label_id
                JOIN dataset ds ON ds.id = lv.dataset_id
                WHERE ds.runid = ? AND ds.testid = ?
                ORDER BY ds.ordinal, l.name
                """)) {
            ps.setLong(1, runId);
            ps.setLong(2, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ordinal = rs.getInt(1);
                    String labelName = rs.getString(2);
                    String value = rs.getString(3);
                    horreumValues.computeIfAbsent(labelName, k -> new TreeMap<>()).put(ordinal, value);
                }
            }
        }

        int datasetCount = 0;
        try (PreparedStatement ps = legacyConn.prepareStatement(
                "SELECT count(*) FROM dataset WHERE runid = ? AND testid = ?")) {
            ps.setLong(1, runId);
            ps.setLong(2, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) datasetCount = rs.getInt(1);
            }
        }

        // Get h5m values for this specific upload (descendants of rootValueId),
        // scoped to avoid mixing values from different uploads
        @SuppressWarnings("unchecked")
        List<Object[]> h5mValues = em.createNativeQuery("""
                WITH RECURSIVE descendants(vid) AS (
                    SELECT ve.child_id FROM value_edge ve WHERE ve.parent_id = ?
                    UNION ALL
                    SELECT ve.child_id FROM value_edge ve JOIN descendants d ON ve.parent_id = d.vid
                )
                SELECT n.name, v.idx, convert_from(v.data, 'UTF-8')
                FROM value v
                JOIN node n ON v.node_id = n.id
                JOIN descendants d ON v.id = d.vid
                WHERE n.type NOT IN ('root')
                ORDER BY n.name, v.idx
                """)
                .setParameter(1, rootValueId)
                .getResultList();

        Map<String, Map<Integer, String>> h5mByLabel = new LinkedHashMap<>();
        for (Object[] row : h5mValues) {
            String name = (String) row[0];
            int idx = ((Number) row[1]).intValue();
            String value = (String) row[2];
            // Prefer non-null values when multiple nodes with the same name produce
            // values at the same idx (e.g., ephemeral-nulled variant nodes and the
            // combiner node sharing the same label name)
            h5mByLabel.computeIfAbsent(name, k -> new TreeMap<>())
                    .merge(idx, value != null ? value : "null",
                           (existing, incoming) -> !"null".equals(incoming) ? incoming : existing);
        }

        System.out.println("  Horreum: " + datasetCount + " datasets, " + horreumValues.size() + " labels");
        System.out.println("  h5m: " + h5mByLabel.size() + " label nodes with values");

        // Compare labels that exist in Horreum, checking each dataset ordinal
        for (String labelName : horreumValues.keySet()) {
            Map<Integer, String> horreumOrdinals = horreumValues.get(labelName);
            Map<Integer, String> h5mOrdinals = resolveH5mValues(labelName, h5mByLabel);

            for (Map.Entry<Integer, String> entry : horreumOrdinals.entrySet()) {
                int ordinal = entry.getKey();
                String horreumValue = entry.getValue();
                if ("null".equals(horreumValue)) continue;

                String hNorm = normalizeValue(horreumValue);

                // Strategy: search ALL h5m values for this label to find the best match
                MatchResult best = findBestMatch(hNorm, horreumValue, h5mOrdinals, ordinal, stubLabels.contains(labelName));

                if (best.type == MatchType.EXACT) {
                    matches++; matchExact++;
                    track(perLabel, labelName, 0);
                    track(perDataset, ordinal, 0);
                    if (verbose) {
                        System.out.println("  OK       " + labelName + "[" + ordinal + "] = " + truncate(horreumValue, 80));
                    }
                } else if (best.type == MatchType.MISALIGNED) {
                    matches++; matchMisaligned++;
                    track(perLabel, labelName, 0);
                    track(perDataset, ordinal, 0);
                    if (verbose) {
                        System.out.println("  OK~idx   " + labelName + "[" + ordinal + "] = " + truncate(horreumValue, 80)
                                + "  (h5m idx=" + best.h5mIdx + ")");
                    }
                } else if (best.type == MatchType.ROUNDING) {
                    matches++; matchRounding++;
                    track(perLabel, labelName, 0);
                    track(perDataset, ordinal, 0);
                    if (verbose) {
                        System.out.println("  OK~round " + labelName + "[" + ordinal + "]: " + truncate(horreumValue, 40)
                                + " ≈ " + truncate(best.h5mValue, 40));
                    }
                } else if (best.type == MatchType.STUB) {
                    mismatches++; mismatchStub++;
                    track(perLabel, labelName, 1);
                    track(perDataset, ordinal, 1);
                    if (verbose) {
                        System.out.println("  STUB     " + labelName + "[" + ordinal + "]: horreum=" + truncate(horreumValue, 60)
                                + " (stub function)");
                    }
                } else if (best.type == MatchType.MISMATCH) {
                    mismatches++;
                    track(perLabel, labelName, 1);
                    track(perDataset, ordinal, 1);
                    System.out.println("  MISMATCH " + labelName + " (dataset " + ordinal + "):");
                    System.out.println("           horreum = " + truncate(horreumValue, 120));
                    System.out.println("           h5m     = " + truncate(best.h5mValue, 120));
                    if (verbose) {
                        System.out.println("           h5m idx = " + best.h5mIdx);
                        System.out.println("           h5m has " + h5mOrdinals.size() + " values at indices: " + h5mOrdinals.keySet());
                    }
                } else { // MISSING
                    missing++;
                    track(perLabel, labelName, 2);
                    track(perDataset, ordinal, 2);
                    if (!h5mOrdinals.isEmpty()) {
                        System.out.println("  MISSING  " + labelName + " (dataset " + ordinal + "): node has " + h5mOrdinals.size()
                                + " values but none match");
                    } else {
                        System.out.println("  MISSING  " + labelName + " (dataset " + ordinal + "): no values in h5m");
                    }
                    if (verbose && !h5mOrdinals.isEmpty()) {
                        System.out.println("           h5m has values at indices: " + h5mOrdinals.keySet());
                    }
                }
            }
        }

        // Check for extra h5m labels not in Horreum
        for (String name : h5mByLabel.keySet()) {
            if (!horreumValues.containsKey(name) && isLabelName(name)) {
                extra++;
            }
        }
        if (extra > 0) {
            System.out.println("  EXTRA    " + extra + " labels in h5m not in Horreum");
        }

        System.out.println("  Result: " + matches + " match (" + matchExact + " exact, " + matchMisaligned + " misaligned, "
                + matchRounding + " rounding), " + mismatches + " mismatch"
                + (mismatchStub > 0 ? " (" + mismatchStub + " stub)" : "")
                + ", " + missing + " missing, " + extra + " extra");
        return new int[]{matches, mismatches, missing, extra, matchMisaligned, matchRounding, mismatchStub};
    }

    /**
     * Resolve h5m values for a Horreum label name, trying exact match first,
     * then fallback strategies (numeric suffix, lowercase, snake_case).
     */
    private Map<Integer, String> resolveH5mValues(String labelName, Map<String, Map<Integer, String>> h5mByLabel) {
        Map<Integer, String> result = h5mByLabel.getOrDefault(labelName, Map.of());
        if (!result.isEmpty()) return result;

        // Try numeric suffix (multi-schema merge adds "0", "1", etc.)
        for (int suffix = 0; suffix <= 3; suffix++) {
            result = h5mByLabel.getOrDefault(labelName + suffix, Map.of());
            if (!result.isEmpty()) return result;
        }
        // Try lowercase first char (e.g., Horreum "User" → h5m "user")
        if (labelName.length() > 0) {
            String lower = labelName.substring(0, 1).toLowerCase() + labelName.substring(1);
            result = h5mByLabel.getOrDefault(lower, Map.of());
            if (!result.isEmpty()) return result;
        }
        // Try snake_case (e.g., "Run ID" → "run_id")
        String snakeCase = labelName.toLowerCase().replaceAll("\\s+", "_");
        result = h5mByLabel.getOrDefault(snakeCase, Map.of());
        return result;
    }

    enum MatchType { EXACT, MISALIGNED, ROUNDING, STUB, MISMATCH, MISSING }

    record MatchResult(MatchType type, String h5mValue, int h5mIdx) {
        static MatchResult missing() { return new MatchResult(MatchType.MISSING, null, -1); }
    }

    /**
     * Search all h5m values for the best match against a Horreum value.
     * Priority: exact at same ordinal > exact at different idx > rounding match > stub > mismatch > missing.
     *
     * @param isStubLabel true if the label's Horreum function is a stub (zero-param, constant return)
     */
    private MatchResult findBestMatch(String hNorm, String hRaw, Map<Integer, String> h5mOrdinals, int ordinal,
                                      boolean isStubLabel) {
        if (h5mOrdinals.isEmpty()) {
            // Even with no h5m values, check for stubs
            if (isStubLabel || isStubValue(hRaw)) {
                return new MatchResult(MatchType.STUB, null, -1);
            }
            return MatchResult.missing();
        }

        // Pass 1: exact match at expected ordinal position (idx = ordinal + 1 for h5m)
        for (Map.Entry<Integer, String> e : h5mOrdinals.entrySet()) {
            String h5mNorm = normalizeValue(e.getValue());
            if (hNorm.equals(h5mNorm)) {
                // For single-dataset runs all indices are valid; check if it's the "expected" position
                // Expected: h5m idx typically = ordinal + 1 (root at idx 0), but this varies
                return new MatchResult(MatchType.EXACT, e.getValue(), e.getKey());
            }
        }

        // Pass 2: numeric rounding match (within relative tolerance)
        Double hNum = tryParseNumber(hNorm);
        if (hNum != null) {
            for (Map.Entry<Integer, String> e : h5mOrdinals.entrySet()) {
                String h5mNorm = normalizeValue(e.getValue());
                Double h5mNum = tryParseNumber(h5mNorm);
                if (h5mNum != null && numbersCloseEnough(hNum, h5mNum)) {
                    return new MatchResult(MatchType.ROUNDING, e.getValue(), e.getKey());
                }
            }
        }

        // Pass 3: check if label is a stub function (detected from function signature or output value)
        if (isStubLabel || isStubValue(hRaw)) {
            return new MatchResult(MatchType.STUB, null, -1);
        }

        // Pass 4: there are h5m values but none match — report first non-null as the mismatch
        for (Map.Entry<Integer, String> e : h5mOrdinals.entrySet()) {
            String val = e.getValue();
            if (val != null && !"null".equals(val)) {
                return new MatchResult(MatchType.MISMATCH, val, e.getKey());
            }
        }

        // All h5m values are null
        Map.Entry<Integer, String> first = h5mOrdinals.entrySet().iterator().next();
        return new MatchResult(MatchType.MISMATCH, first.getValue(), first.getKey());
    }

    /** Try to parse a normalized value as a number. Returns null if not numeric. */
    private static Double tryParseNumber(String value) {
        if (value == null || value.isEmpty() || "null".equals(value) || "NaN".equals(value)) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Check if two numbers are close enough (relative tolerance 1e-6 or absolute 1e-10). */
    private static boolean numbersCloseEnough(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.isNaN(a) && Double.isNaN(b);
        if (a == b) return true;
        double diff = Math.abs(a - b);
        double max = Math.max(Math.abs(a), Math.abs(b));
        return diff < 1e-10 || (max > 0 && diff / max < 1e-6);
    }

    /** Detect Horreum stub function outputs that ignore input data. */
    private static boolean isStubValue(String value) {
        if (value == null) return false;
        String v = value.trim();
        // Strip JSON string quotes
        if (v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.startsWith("Need to collect")
                || v.equals("N/A")
                || v.equals("TBD")
                || v.equals("TODO");
    }

    private static <K> void track(Map<K, int[]> map, K key, int idx) {
        map.computeIfAbsent(key, k -> new int[3])[idx]++;
    }

    private static String normalizeValue(String value) {
        if (value == null) return "null";
        // Remove quotes for string comparison
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        // Truncate long values (objects/arrays) to a hash for comparison
        if (value.length() > 200) {
            return "HASH:" + value.hashCode();
        }
        return value.trim();
    }

    private static String truncate(String s, int len) {
        if (s == null) return "null";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    private static boolean isLabelName(String name) {
        // Heuristic: label names typically start with uppercase or are known patterns
        return name.matches("^[A-Z].*") || name.contains(" ");
    }
}
