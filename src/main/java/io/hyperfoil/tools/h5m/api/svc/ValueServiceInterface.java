package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.Value;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing Values.
 */
public interface ValueServiceInterface {

    /**
     * Creates a root value in a folder and kicks off the calculation pipeline.
     * Use {@link ProcessingServiceInterface#awaitIngestion} to wait for ingestion to complete.
     *
     * @param folderId The ID of the folder.
     * @param data The JSON data.
     * @return the root value ID
     */
    long createRootValue(long folderId, JqValue data);

    /**
     * Purges all values.
     */
    void purgeValues();

    /**
     * Retrieves the descendant values of a specific node.
     *
     * @param nodeId The ID of the node.
     * @return A list of descendant values for the given node.
     */
    List<Value> getNodeDescendantValues(Long nodeId);

    /**
     * Retrieves grouped values for a specific node.
     *
     * @param nodeId The ID of the node.
     * @return A list of JSON nodes representing the grouped values.
     */
    List<JqValue> getGroupedValues(Long nodeId);


    /**
     * Retrieves all values produced by a specific node.
     *
     * @param nodeId The ID of the node.
     * @return A list of values for the given node.
     */
    List<Value> getNodeValues(Long nodeId);

    /**
     * Returns the total count of values for a specific node.
     *
     * @param nodeId The ID of the node.
     * @return The count of values.
     */
    long getNodeValueCount(Long nodeId);

    /**
     * Retrieves the most recent values for a specific node, ordered by id descending.
     *
     * @param nodeId The ID of the node.
     * @param limit Maximum number of values to return.
     * @return A list of the most recent values, ordered oldest to newest.
     */
    List<Value> getNodeValuesPage(Long nodeId, int limit);

    /**
     * Returns detection node values that are descendants of the given root value.
     *
     * @param rootValueId the root value ID (upload ID)
     * @return detection values as DTOs, or empty list if none found
     */
    List<Value> getDetectionDescendants(long rootValueId);

    /**
         * Returns one row per upload for a folder, with each row containing the values of the requested nodes.
         * groupByNodeId and sortByNodeId are always included in every row regardless of nodeIds.
         * @param folderId Folder Id to query.
         * @param nodeIds Node IDs to include as columns (null/empty list = all nodes).
         * @param groupByNodeId Node whose value identifies the series (e.g. config fingerprint).
         * @param sortByNodeId  Node whose value orders the rows (acts as X-axis).
       */
    List<JqValue> getLabelValues(Long folderId,Long groupByNodeId, List<Long> nodeIds, Long sortByNodeId);
    /**
     * Retrieves grouped values for a specific node, optionally filtered to specific node IDs.
     *
     * @param nodeId The ID of the root node.
     * @param filterNodeIds Optional list of node IDs to include. If null, all nodes are included.
     * @return A list of JSON nodes representing the grouped values.
     */
    List<JqValue> getGroupedValues(Long nodeId, List<Long> filterNodeIds);

    List<JqValue> getGroupedValues(Long nodeId, List<Long> filterNodeIds, Map<Long,JqValue> fingerprints, Long sortByNodeId);
    List<JqValue> getGroupedValues(Long nodeId, Long valueId, List<Long> filterNodeIds, Map<Long,JqValue> fingerprints, Long sortByNodeId);


}
