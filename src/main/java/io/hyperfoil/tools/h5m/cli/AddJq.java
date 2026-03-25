package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Callable;

@CommandLine.Command(name="jq", separator = " ", description = "add jq node", mixinStandardHelpOptions = true)
public class AddJq implements Callable<Integer> {

    @CommandLine.Option(names = {"to"},description = "target group / test" ) String groupName;
    @CommandLine.Parameters(index="0",arity="0..1",description = "node name") String name;
    @CommandLine.Parameters(index="1",arity="0..1",description = "jq filter") String jq;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public Integer call() throws Exception {
        Scanner sc = new Scanner(System.in);
        if(name == null && H5m.consoleAttached()){
            System.out.printf("Enter name: ");
            name = sc.nextLine();
        }
        NodeGroup foundGroup;
        do{
            if(groupName == null && H5m.consoleAttached()){
                System.out.printf("Enter target group / folder name: ");
                groupName = sc.nextLine();
            }
            foundGroup =  nodeGroupService.byName(groupName);
            if(foundGroup == null){
                System.err.println("could not find "+groupName);
                groupName = null;
            }
        }while(groupName == null && H5m.consoleAttached());

        if("-".equals(jq)){
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                }
            }
            if(sb.length()>0){
                jq = sb.toString().trim();
            }else{
                System.err.println("unable to read function from input");
                return 1;
            }
        }
        if(jq == null && H5m.consoleAttached()){
            System.out.printf("Enter jq filter: ");
            jq = sc.nextLine();
        }

        nodeService.create(name, foundGroup.id(), NodeType.JQ, jq);

        return 0;
    }
}
