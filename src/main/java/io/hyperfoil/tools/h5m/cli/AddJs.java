package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.node.JsNode;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

@CommandLine.Command(name="js", separator = " ", description = "add javascript node", mixinStandardHelpOptions = true)
public class AddJs implements Callable<Integer> {

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @CommandLine.Option(names = {"to"},description = "target group / test" ) String groupName;

    @CommandLine.Parameters(index="0",arity="1",description = "node name") String name;
    @CommandLine.Parameters(index="1",arity="1",description = "javascript function") String function;

    @Override
    public Integer call() throws Exception {

        if("-".equals(function)){
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                }
            }
            if(sb.length()>0){
                function = sb.toString().trim();
            }else{
                System.err.println("unable to read function from input");
                return 1;
            }
        }
        if(function == null || "-".equals(function)){
            System.err.println("unable to read function from input "+function);
            return 1;
        }
        if(groupName == null){
            System.err.println("missing group name");
            return 1;
        }

        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if(foundGroup == null){
            System.err.println("unable to find group: "+groupName);
            return 1;
        }

        nodeService.create(name, foundGroup.id(), NodeType.JS, function);

        return 0;
    }
}
