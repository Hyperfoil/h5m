package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.FolderSummary;
import io.hyperfoil.tools.h5m.api.Upload;
import io.hyperfoil.tools.h5m.svc.RecalculationTracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service interface for managing Folders.
 */
public interface FolderServiceInterface {

    /**
     * Retrieves all the folders;
     *
     * @return A list of all the folders.
     */
    List<Folder> list();

    /**
     * Retrieves a folder by its name.
     *
     * @param name The name of the folder.
     * @return The folder with the given name.
     */
    Folder find(String name);

    /**
     * Gets the upload count for all folders.
     *
     * @return A map of folder names to their upload counts.
     */
    Map<String, Integer> getFolderUploadCount();

    /**
     * Creates a new folder with the given name.
     *
     * @param name The name of the folder to create.
     * @return The created folder.
     */
    Folder create(String name);

    /**
     * Deletes a folder by its ID.
     *
     * @param id The ID of the folder to delete.
     */
    void delete(long id);

    /**
     * Uploads data to a folder.
     * Returns immediately with an {@link Upload} containing the upload ID and a future
     * that completes when all processing finishes.
     *
     * @param folderId The ID of the folder.
     * @param data The JSON data to upload.
     * @return an Upload with the upload ID (safe to return to callers) and
     *         a future (for callers that need to await completion).
     */
    Upload upload(long folderId, JqValue data);

    /**
     * Selectively recalculates values for a specific node and its dependents.
     *
     * @param nodeId The ID of the node to recalculate.
     * @return tracker with progress and completion future
     */
    RecalculationTracker recalculateNode(long nodeId);

    /**
     * Retrieves the structural representation of a folder.
     *
     * @param folderId The ID of the folder.
     * @return The JSON representation of the folder's structure.
     */
    JqValue structure(long folderId);

    /**
     * Retrieves dashboard summaries for all folders.
     *
     * @return A list of folder summaries with upload counts, node counts, and change counts.
     */
    List<FolderSummary> getDashboardSummaries();

    /**
     * Exports a folder's node graph to a JSON file.
     *
     * @param folderId The folder ID.
     * @param outputPath Path to write the JSON file.
     */
    void export(long folderId, Path outputPath) throws IOException;

    /**
     * Imports a folder and its node graph from a JSON file.
     *
     * @param inputPath Path to the JSON file.
     * @param overwrite If true, delete existing folder before importing.
     * @return The imported folder.
     */
    Folder importFolder(Path inputPath, boolean overwrite) throws IOException;

}
