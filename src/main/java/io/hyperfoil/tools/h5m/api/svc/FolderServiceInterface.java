package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.yaup.json.Json;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing Folders.
 */
public interface FolderServiceInterface {

    /**
     * Retrieves a folder by its name.
     *
     * @param name The name of the folder.
     * @return The folder with the given name.
     */
    Folder byName(String name);

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
     * @return The ID of the created folder.
     */
    long create(String name);

    /**
     * Deletes a folder by its name.
     *
     * @param name The name of the folder to delete.
     * @return The ID of the deleted folder.
     */
    long delete(String name);

    /**
     * Uploads data to a specific path within a folder.
     *
     * @param name The name of the folder.
     * @param path The path within the folder.
     * @param data The JSON data to upload.
     */
    ValueEntity upload(String name, String path, JsonNode data);

    List<ValueEntity> getDetectionValues(String name, List<ValueEntity> rootValues);

    /**
     * Recalculates the contents or state of a folder by its name.
     *
     * @param name The name of the folder to recalculate.
     */
    List<Long> recalculate(String name);

    /**
     * Retrieves the structural representation of a folder.
     *
     * @param name The name of the folder.
     * @return The JSON representation of the folder's structure.
     */
    Json structure(String name);

}
