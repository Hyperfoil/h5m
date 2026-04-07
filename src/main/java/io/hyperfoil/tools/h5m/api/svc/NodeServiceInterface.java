package io.hyperfoil.tools.h5m.api.svc;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeType;

import java.util.List;

/**
 * Service interface for managing Nodes.
 */
public interface NodeServiceInterface {

    /**
     * Creates a new node with an operation.
     *
     * @param name      The name of the node.
     * @param groupId   The ID of the group the node belongs to.
     * @param type      The type of the node.
     * @param operation The operation associated with the node.
     * @return The ID of the created node.
     */
    Long create(String name, Long groupId, NodeType type, String operation);

    /**
     * Creates a new node with sources and configuration.
     *
     * @param name          The name of the node.
     * @param groupId       The ID of the group the node belongs to.
     * @param type          The type of the node.
     * @param sources       A list of source node IDs.
     * @param configuration The configuration object for the node.
     * @return The ID of the created node.
     * @throws JsonProcessingException If there is an error processing the configuration JSON.
     */
    Long createConfigured(String name, Long groupId, NodeType type, List<Long> sources, Object configuration) throws JsonProcessingException;

    /**
     * Deletes a node by its ID.
     *
     * @param nodeId The ID of the node to delete.
     */
    void delete(Long nodeId);

    /**
     * Finds nodes by their fully qualified domain name (FQDN) within a specific group.
     *
     * @param name    The FQDN of the node.
     * @param groupId The ID of the group.
     * @return A list of matching nodes within the group.
     */
    List<Node> findNodeByFqdn(String name, Long groupId);

    /**
     * Finds nodes by their fully qualified domain name (FQDN).
     * Used by CLI commands. Not exposed as a REST endpoint.
     *
     * @param fqdn The FQDN of the node.
     * @return A list of matching nodes.
     */
    List<Node> findNodeByFqdn(String fqdn);

}
