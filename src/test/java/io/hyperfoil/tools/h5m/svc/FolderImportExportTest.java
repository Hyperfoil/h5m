package io.hyperfoil.tools.h5m.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.node.JqNode;
import io.hyperfoil.tools.h5m.entity.node.RootNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class FolderImportExportTest extends FreshDb {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    FolderService folderService;

    @Inject
    TransactionManager tm;

    @Test
    public void export_and_import_roundtrip() throws Exception {
        // Create a folder with a small node graph: root -> a -> b, root -> c
        tm.begin();
        long folderId = folderService.create("roundtrip-test");
        FolderEntity folder = folderService.read(folderId);
        NodeEntity root = folder.group.root;

        JqNode nodeA = new JqNode("a", ".a", root);
        nodeA.group = folder.group;
        nodeA.persist();
        folder.group.sources.add(nodeA);

        JqNode nodeB = new JqNode("b", ".b", nodeA);
        nodeB.group = folder.group;
        nodeB.persist();
        folder.group.sources.add(nodeB);

        JqNode nodeC = new JqNode("c", ".c", root);
        nodeC.group = folder.group;
        nodeC.persist();
        folder.group.sources.add(nodeC);

        folder.group.persist();
        tm.commit();

        // Export
        Path exportFile = Files.createTempFile("h5m-export-", ".json");
        try {
            folderService.export("roundtrip-test", exportFile);

            // Verify export file
            JsonNode exported = MAPPER.readTree(exportFile.toFile());
            assertEquals("roundtrip-test", exported.get("folder").asText());
            JsonNode nodes = exported.get("nodes");
            assertEquals(4, nodes.size(), "Should export root + 3 nodes");
            assertEquals("root", nodes.get(0).get("type").asText(), "First node should be root");

            // Delete the original folder
            tm.begin();
            folderService.delete("roundtrip-test");
            tm.commit();

            // Verify it's gone
            assertNull(folderService.byName("roundtrip-test"), "Folder should be deleted");

            // Import
            String importedName = folderService.importFolder(exportFile, false);
            assertEquals("roundtrip-test", importedName);

            // Verify the imported folder
            assertNotNull(folderService.byName("roundtrip-test"), "Folder should exist after import");

            // Verify node count
            tm.begin();
            FolderEntity imported = folderService.read(
                folderService.byName("roundtrip-test").id()
            );
            // group.sources contains all non-root nodes
            int nodeCount = imported.group.sources.size();
            tm.commit();
            assertEquals(3, nodeCount, "Should have 3 non-root nodes after import");

        } finally {
            Files.deleteIfExists(exportFile);
        }
    }

    @Test
    public void import_skips_existing_folder() throws Exception {
        tm.begin();
        folderService.create("existing-folder");
        tm.commit();

        Path exportFile = Files.createTempFile("h5m-export-", ".json");
        try {
            // Create a minimal export file with a different node structure
            Files.writeString(exportFile, """
                {
                  "folder": "existing-folder",
                  "nodes": [
                    {"id": 1, "name": "", "type": "root", "operation": null, "sources": []},
                    {"id": 2, "name": "x", "type": "jq", "operation": ".x", "sources": [1]}
                  ]
                }
                """);

            // Import without overwrite — should skip
            String result = folderService.importFolder(exportFile, false);
            assertEquals("existing-folder", result);

            // Verify the original folder is unchanged (no extra nodes)
            tm.begin();
            FolderEntity folder = folderService.read(
                folderService.byName("existing-folder").id()
            );
            int nodeCount = folder.group.sources.size();
            tm.commit();
            assertEquals(0, nodeCount, "Original folder should have 0 non-root nodes (import was skipped)");

        } finally {
            Files.deleteIfExists(exportFile);
        }
    }

    @Test
    public void import_with_overwrite_replaces_folder() throws Exception {
        tm.begin();
        folderService.create("overwrite-test");
        tm.commit();

        Path exportFile = Files.createTempFile("h5m-export-", ".json");
        try {
            Files.writeString(exportFile, """
                {
                  "folder": "overwrite-test",
                  "nodes": [
                    {"id": 1, "name": "", "type": "root", "operation": null, "sources": []},
                    {"id": 2, "name": "x", "type": "jq", "operation": ".x", "sources": [1]},
                    {"id": 3, "name": "y", "type": "jq", "operation": ".y", "sources": [1]}
                  ]
                }
                """);

            // Import with overwrite
            String result = folderService.importFolder(exportFile, true);
            assertEquals("overwrite-test", result);

            // Verify the folder was replaced with the new structure
            tm.begin();
            FolderEntity folder = folderService.read(
                folderService.byName("overwrite-test").id()
            );
            int nodeCount = folder.group.sources.size();
            tm.commit();
            assertEquals(2, nodeCount, "Overwritten folder should have 2 non-root nodes");

        } finally {
            Files.deleteIfExists(exportFile);
        }
    }

    @Test
    public void export_preserves_node_types_and_operations() throws Exception {
        tm.begin();
        long folderId = folderService.create("types-test");
        FolderEntity folder = folderService.read(folderId);
        NodeEntity root = folder.group.root;

        JqNode jqNode = new JqNode("cpu", ".results.cpu", root);
        jqNode.group = folder.group;
        jqNode.persist();
        folder.group.sources.add(jqNode);

        folder.group.persist();
        tm.commit();

        Path exportFile = Files.createTempFile("h5m-export-", ".json");
        try {
            folderService.export("types-test", exportFile);

            JsonNode exported = MAPPER.readTree(exportFile.toFile());
            JsonNode nodes = exported.get("nodes");

            // Find the jq node
            JsonNode jq = nodes.get(1);
            assertEquals("cpu", jq.get("name").asText());
            assertEquals("jq", jq.get("type").asText());
            assertEquals(".results.cpu", jq.get("operation").asText());
            assertEquals(1, jq.get("sources").size());

        } finally {
            Files.deleteIfExists(exportFile);
        }
    }

}
