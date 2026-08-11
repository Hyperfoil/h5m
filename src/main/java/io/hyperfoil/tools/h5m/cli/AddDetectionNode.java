package io.hyperfoil.tools.h5m.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;

/**
 * Abstract base class for detection node commands (fixed threshold, relative difference,
 * standard deviation anomaly, e-divisive). Encapsulates the common folder resolution,
 * range/groupBy/fingerprint node lookup, and fingerprint node creation logic.
 * <p>
 * Subclasses declare their own unique {@code @Option} fields and implement
 * {@link #createDetectionNode} to create the specific detection node type.
 */
public abstract class AddDetectionNode implements Command<H5mCommandInvocation>, FolderAware {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    String folderName;

    @Option(name = "range", acceptNameWithoutDashes = true, description = "node that produces the value to inspect",
            completer = NodeNameCompleter.class)
    String rangeName;

    @Option(name = "by", acceptNameWithoutDashes = true, description = "grouping node",
            completer = NodeNameCompleter.class)
    String groupBy;

    @OptionList(name = "fingerprint", description = "node names to use as fingerprint",
            completer = NodeNameCompleter.class)
    List<String> fingerprints;

    @Option(name = "fingerprint-filter", acceptNameWithoutDashes = true, shortName = 'f',
            description = "jq filter expression for fingerprints")
    String fingerprintFilter;

    @Argument(description = "node name")
    String name;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    /**
     * Domain node name. Subclasses that support a domain option should declare
     * their own {@code @Option(name = "domain")} field and set this value.
     * FixedThreshold does not use a domain node.
     */
    protected String domainName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (name == null || name.isEmpty()) {
            invocation.println("missing node name");
            return CommandResult.FAILURE;
        }
        if (folderName == null || folderName.isEmpty()) {
            invocation.println("folder name is required (use --to)");
            return CommandResult.FAILURE;
        }

        NodeGroup foundGroup = nodeGroupService.find(folderName);
        if (foundGroup == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        // Warn about duplicate node name
        List<Node> foundNodes = nodeService.findNodeByFqdn(name, foundGroup.id());
        if (!foundNodes.isEmpty()) {
            invocation.println(folderName + " already has " + name + " node(s)\n  "
                    + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
        }

        // Resolve range node (required)
        if (rangeName == null || rangeName.isEmpty()) {
            invocation.println("Missing range");
            return CommandResult.FAILURE;
        }
        foundNodes = nodeService.findNodeByFqdn(rangeName, foundGroup.id());
        if (foundNodes.isEmpty()) {
            invocation.println("Node '" + rangeName + "' not found");
            return CommandResult.FAILURE;
        } else if (foundNodes.size() > 1) {
            invocation.println("'" + rangeName + "' is ambiguous, matched multiple nodes:\n  "
                    + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
            return CommandResult.FAILURE;
        }
        Node rangeNode = foundNodes.getFirst();

        // Resolve domain node (optional — subclasses set domainName)
        Node domainNode = null;
        if (domainName != null && !domainName.isEmpty()) {
            foundNodes = nodeService.findNodeByFqdn(domainName, foundGroup.id());
            if (foundNodes.isEmpty()) {
                invocation.println("Node '" + domainName + "' not found");
                return CommandResult.FAILURE;
            } else if (foundNodes.size() > 1) {
                invocation.println("'" + domainName + "' is ambiguous, matched multiple nodes:\n  "
                        + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return CommandResult.FAILURE;
            }
            domainNode = foundNodes.getFirst();
        }

        // Resolve groupBy node (optional, defaults to root)
        Node groupByNode = null;
        if (groupBy != null && !groupBy.isEmpty()) {
            foundNodes = nodeService.findNodeByFqdn(groupBy, foundGroup.id());
            if (foundNodes.isEmpty()) {
                invocation.println("Node '" + groupBy + "' not found");
                return CommandResult.FAILURE;
            } else if (foundNodes.size() > 1) {
                invocation.println("'" + groupBy + "' is ambiguous, matched multiple nodes:\n  "
                        + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return CommandResult.FAILURE;
            }
            groupByNode = foundNodes.getFirst();
        }
        if (groupByNode == null) {
            groupByNode = foundGroup.root();
        }

        // Resolve fingerprint nodes
        List<Long> fingerprintNodeIds = new ArrayList<>();
        if (fingerprints != null && !fingerprints.isEmpty()) {
            for (String fingerprintName : fingerprints) {
                foundNodes = nodeService.findNodeByFqdn(fingerprintName, foundGroup.id());
                if (foundNodes.isEmpty()) {
                    invocation.println("Node '" + fingerprintName + "' not found");
                    return CommandResult.FAILURE;
                } else if (foundNodes.size() > 1) {
                    invocation.println("'" + fingerprintName + "' is ambiguous, matched multiple nodes:\n  "
                            + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                    return CommandResult.FAILURE;
                }
                fingerprintNodeIds.add(foundNodes.getFirst().id());
            }
        }

        // Create fingerprint node and delegate detection node creation to subclass
        try {
            Node fingerprintNode = nodeService.createConfigured("_fp-" + name, foundGroup.id(),
                    NodeType.FINGERPRINT, fingerprintNodeIds, null);
            return createDetectionNode(invocation, foundGroup, fingerprintNode,
                    rangeNode, domainNode, groupByNode);
        } catch (Exception e) {
            invocation.println("Error creating node: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    /**
     * Create the specific detection node. Called after common validation and
     * fingerprint node creation.
     *
     * @param invocation     the command invocation
     * @param group          the resolved node group
     * @param fingerprintNode the created fingerprint node
     * @param rangeNode      the resolved range node
     * @param domainNode     the resolved domain node (null if not provided)
     * @param groupByNode    the resolved groupBy node (defaults to root)
     * @return SUCCESS or FAILURE
     */
    protected abstract CommandResult createDetectionNode(
            H5mCommandInvocation invocation,
            NodeGroup group,
            Node fingerprintNode,
            Node rangeNode,
            Node domainNode,
            Node groupByNode);

    @Override
    public String getFolderName() { return folderName; }
}
