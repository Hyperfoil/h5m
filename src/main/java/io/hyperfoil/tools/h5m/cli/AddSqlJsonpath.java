package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name="sqlpath", separator = " ", description = "add sql jsonpath node", mixinStandardHelpOptions = true)
public class AddSqlJsonpath implements Callable<Integer> {

    @CommandLine.Option(names = {"to"},description = "target group / test" ) String groupName;
    @CommandLine.Parameters(index="0",arity="1",description = "node name") String name;
    @CommandLine.Parameters(index="1",arity="1",description = "jsonpath") String jsonpath;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;


    @Override
    public Integer call() throws Exception {

        if(name == null){
            System.err.println("missing node name");
            return 1;
        }
        if(groupName == null){
            System.err.println("missing group name");
            return 1;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if(foundGroup == null){
            System.err.println("could not find target group/test "+groupName);
            return 1;
        }
        if(jsonpath == null){
            System.err.println("missing jsonpath");
            return 1;
        }

        nodeService.create(name, foundGroup.id(), NodeType.SQL_JSONPATH_NODE, jsonpath);

        return 0;
    }
}
