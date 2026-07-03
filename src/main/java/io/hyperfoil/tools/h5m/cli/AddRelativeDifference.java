package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.RelativeDifferenceConfig;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@CommandDefinition(name="relativedifference", description = "Add a relative difference change detection node that detects percentage changes between consecutive values", generateHelp = true)
public class AddRelativeDifference implements Command<H5mCommandInvocation> {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test") String groupName;

    @Option(name = "range", acceptNameWithoutDashes = true, description = "node that produces the value to inspect")
    String rangeName;
    @Option(name = "domain", acceptNameWithoutDashes = true, description = "node used to sort the rang values")
    String domainName;
    @Option(name = "threshold", acceptNameWithoutDashes = true, description = "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.", defaultValue = {""+RelativeDifference.DEFAULT_THRESHOLD})
    double threshold;
    @Option(name = "window", acceptNameWithoutDashes = true, description = "Number of most recent datapoints used for aggregating the value for comparison.", defaultValue = {""+RelativeDifference.DEFAULT_WINDOW})
    int window;
    @Option(name = "minPrevious", acceptNameWithoutDashes = true, description = "Number of datapoints preceding the aggregation window.", defaultValue = {""+RelativeDifference.DEFAULT_MIN_PREVIOUS})
    int minPrevious;
    @Option(name = "filter", acceptNameWithoutDashes = true, description = "Function used to aggregate datapoints from the floating window.", defaultValue = {RelativeDifference.DEFAULT_FILTER})
    String filter;
    @Option(name = "fingerprint", acceptNameWithoutDashes = true, description = "node names to use as fingerprint")
    List<String> fingerprints;
    @Option(name = "fingerprint-filter", acceptNameWithoutDashes = true, shortName = 'f', description = "jq filter expression for fingerprints")
    String fingerprintFilter;

    @Option(name = "by", acceptNameWithoutDashes = true, description = "grouping node")
    public String groupBy;

    @Argument(description = "node name") String name;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
        if(name==null || name.isEmpty()){
            invocation.println("missing node name");
            return CommandResult.FAILURE;
        }
        if(groupName==null || groupName.isEmpty()){
            invocation.println("missing group name");
            return CommandResult.FAILURE;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if(foundGroup==null){
            invocation.println("node group with name "+groupName+" does not exist");
            return CommandResult.FAILURE;
        }

        List<Node> foundNodes = nodeService.findNodeByFqdn(name,foundGroup.id());
        if(!foundNodes.isEmpty()){
            invocation.println(groupName+" already has "+name+" node(s)\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
        }

        if(rangeName==null || rangeName.isEmpty()){
            invocation.println("Missing range");
            return CommandResult.FAILURE;
        }
        foundNodes = nodeService.findNodeByFqdn(rangeName,foundGroup.id());
        if(foundNodes.isEmpty()){
            invocation.println("could not find matching range node by name "+rangeName);
            return CommandResult.FAILURE;
        }else if (foundNodes.size()>1){
            invocation.println("found more than one matching range node by name "+rangeName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
            return CommandResult.FAILURE;
        }
        Node rangeNode = foundNodes.getFirst();

        Node domainNode = null;
        if(domainName!=null && !domainName.isEmpty()){
            foundNodes = nodeService.findNodeByFqdn(domainName, foundGroup.id());
            if(foundNodes.isEmpty()){
                invocation.println("could not find matching domain node by name "+domainName);
                return CommandResult.FAILURE;
            }else if (foundNodes.size()>1){
                invocation.println("found more than one matching domain node by name "+domainName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return CommandResult.FAILURE;
            }
            domainNode = foundNodes.getFirst();
        }

        Node groupByNode = null;
        if(groupBy!=null && !groupBy.isEmpty()){
            foundNodes = nodeService.findNodeByFqdn(groupBy, foundGroup.id());
            if(foundNodes.isEmpty()){
                invocation.println("could not find matching group by node with name"+groupBy);
                return CommandResult.FAILURE;
            }else if (foundNodes.size()>1){
                invocation.println("found more than one matching group by node for name "+groupBy+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return CommandResult.FAILURE;
            }
            groupByNode = foundNodes.getFirst();
        }
        if(groupByNode==null){
            groupByNode = foundGroup.root();
        }

        List<Long> fingerprintNodes = new ArrayList<>();
        if(fingerprints!=null && !fingerprints.isEmpty()){
            List<String> fingerprintNames = fingerprints.stream().flatMap(fp->Arrays.stream(fp.split(","))).map(String::trim).filter(v->!v.isBlank()).toList();
            for(String fingeprintName : fingerprintNames){
                foundNodes = nodeService.findNodeByFqdn(fingeprintName,foundGroup.id());
                if(foundNodes.isEmpty()){
                    invocation.println("could not find matching fingerprint node by name "+fingeprintName);
                    return CommandResult.FAILURE;
                }else if (foundNodes.size()>1){
                    invocation.println("found more than one matching fingerprint node by name "+fingeprintName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                    return CommandResult.FAILURE;
                }
                fingerprintNodes.add(foundNodes.getFirst().id());
            }
        }

        try {
            Long fingerprintId = nodeService.createConfigured("_fp-" + name, foundGroup.id(), NodeType.FINGERPRINT, fingerprintNodes, null);
            List<Long> sources = domainNode == null ? List.of(fingerprintId, groupByNode.id(), rangeNode.id()) : List.of(fingerprintId, groupByNode.id(), rangeNode.id(), domainNode.id());
            nodeService.createConfigured(name, foundGroup.id(), NodeType.RELATIVE_DIFFERENCE, sources, new RelativeDifferenceConfig(filter, threshold, window, minPrevious, fingerprintFilter));
        } catch (Exception e) {
            invocation.println("Error creating node: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        return CommandResult.SUCCESS;
    }
}
