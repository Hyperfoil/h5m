package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name="node",separator = " ", description = "remove a node", mixinStandardHelpOptions = true)
public class RemoveNode implements Callable<Integer> {

    @Inject
    NodeServiceInterface nodeService;

    @CommandLine.Parameters(index="0",arity="1",description = "node name") String name;

    @CommandLine.Option(names = {"from"},description = "target group / test",arity = "0..1") String groupName;

    @Override
    public Integer call() throws Exception {
        String fqdn = groupName == null ? name : groupName + NodeEntity.FQDN_SEPARATOR + name;
        List<Node> found = nodeService.findNodeByFqdn(fqdn);
        if(found==null || found.isEmpty()) {
            System.err.println("could not find " + fqdn);
            return 1;
        }else if (found.size()>1){
            System.err.println("found too many matching nodes");
            found.forEach(System.out::println);
            return 1;
        }else{
            nodeService.delete(found.getFirst().id());
        }
        return 0;
    }
}
