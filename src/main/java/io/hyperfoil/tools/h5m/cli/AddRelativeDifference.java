package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.RelativeDifferenceConfig;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name="relativedifference", separator = " ", description = "add a relative difference node", mixinStandardHelpOptions = true)
public class AddRelativeDifference implements Callable<Integer> {

    @CommandLine.Option(names = {"to"},description = "target group / test" ) String groupName;

    @CommandLine.Option(names={"range"}, arity="1",description = "node that produces the value to inspect")
    String rangeName;
    @CommandLine.Option(names={"domain"}, arity="0..1", description = "node used to sort the rang values")
    String domainName;
    @CommandLine.Option(names={"threshold"}, arity="0..1", description = "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.", defaultValue = ""+RelativeDifference.DEFAULT_THRESHOLD)
    double threshold;
    @CommandLine.Option(names={"window"}, arity="0..1",description = "Number of most recent datapoints used for aggregating the value for comparison.", defaultValue = ""+RelativeDifference.DEFAULT_WINDOW)
    int window;
    @CommandLine.Option(names={"minPrevious"}, arity = "0..1", description = "Number of datapoints preceding the aggregation window.", defaultValue = ""+RelativeDifference.DEFAULT_MIN_PREVIOUS)
    int minPrevious;
    @CommandLine.Option(names={"filter"}, arity = "0..1",description = "Function used to aggregate datapoints from the floating window.", defaultValue = RelativeDifference.DEFAULT_FILTER)
    String filter;
    @CommandLine.Option(names={"fingerprint"}, description = "node names to use as fingerprint")
    List<String> fingerprints;
    @CommandLine.Option(names={"--fingerprint-filter", "-ff"}, arity = "0..1", description = "jq filter expression for fingerprints")
    String fingerprintFilter;

    @CommandLine.Option(names = {"by"},description = "grouping node" ,arity = "0..1")
    public String groupBy;

    @CommandLine.Parameters(index="0",arity="1",description = "node name") String name;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public Integer call() throws Exception {
        if(name==null || name.isEmpty()){
            System.err.println("missing node name");
            return 1;
        }
        if(groupName==null || groupName.isEmpty()){
            System.err.println("missing group name");
            return 1;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if(foundGroup==null){
            System.err.println("node group with name "+groupName+" does not exist");
            return 1;
        }

        List<Node> foundNodes = nodeService.findNodeByFqdn(name,foundGroup.id());
        if(!foundNodes.isEmpty()){
            System.err.println(groupName+" already has "+name+" node(s)\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
        }

        if(rangeName==null || rangeName.isEmpty()){
            System.err.println("Missing range");
            return 1;
        }
        foundNodes = nodeService.findNodeByFqdn(rangeName,foundGroup.id());
        if(foundNodes.isEmpty()){
            System.err.println("could not find matching range node by name "+rangeName);
            return 1;
        }else if (foundNodes.size()>1){
            System.err.println("found more than one matching range node by name "+rangeName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
            return 1;
        }
        Node rangeNode = foundNodes.getFirst();

        Node domainNode = null;
        if(domainName!=null && !domainName.isEmpty()){
            foundNodes = nodeService.findNodeByFqdn(domainName, foundGroup.id());
            if(foundNodes.isEmpty()){
                System.err.println("could not find matching domain node by name "+domainName);
                return 1;
            }else if (foundNodes.size()>1){
                System.err.println("found more than one matching domain node by name "+domainName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return 1;
            }
            domainNode = foundNodes.getFirst();
        }

        Node groupByNode = null;
        if(groupBy!=null && !groupBy.isEmpty()){
            foundNodes = nodeService.findNodeByFqdn(groupBy, foundGroup.id());
            if(foundNodes.isEmpty()){
                System.err.println("could not find matching group by node with name"+groupBy);
                return 1;
            }else if (foundNodes.size()>1){
                System.err.println("found more than one matching group by node for name "+groupBy+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return 1;
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
                    System.err.println("could not find matching fingerprint node by name "+fingeprintName);
                    return 1;
                }else if (foundNodes.size()>1){
                    System.err.println("found more than one matching fingerprint node by name "+fingeprintName+"\n  "+foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                    return 1;
                }
                fingerprintNodes.add(foundNodes.getFirst().id());
            }
        }

        Long fingerprintId = nodeService.create("_fp-" + name, foundGroup.id(), NodeType.FINGERPRINT, fingerprintNodes, null);
        List<Long> sources = domainNode == null ? List.of(fingerprintId, groupByNode.id(), rangeNode.id()) : List.of(fingerprintId, groupByNode.id(), rangeNode.id(), domainNode.id());
        nodeService.create(name, foundGroup.id(), NodeType.RELATIVE_DIFFERENCE, sources, new RelativeDifferenceConfig(filter, threshold, window, minPrevious, fingerprintFilter));

        return 0;
    }
}
