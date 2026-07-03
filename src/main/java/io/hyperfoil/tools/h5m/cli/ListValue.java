package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.jjq.value.JqNull;
import io.hyperfoil.tools.jjq.value.JqNumber;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;


import java.util.*;
import java.util.function.Function;

@CommandDefinition(name="values", description = "List computed values in a folder, optionally grouped by a node. Default limit: 50 results", generateHelp = true)
public class ListValue implements Command<H5mCommandInvocation> {

    @Inject
    NodeServiceInterface nodeService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    ValueServiceInterface valueService;

    public enum Format { raw, table }

    @Option(name = "as", acceptNameWithoutDashes = true, description = "presentation option (raw or table)", defaultValue = {"raw"})
    Format format;

    @Option(name = "by", acceptNameWithoutDashes = true, description = "grouping node")
    public String groupBy;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "group name")
    public String groupName;

    @Option(name = "limit", acceptNameWithoutDashes = true, shortName = 'l', description = "maximum number of results to display", defaultValue = { "50" })
    int limit;


    public ListValue() {}
    public ListValue(String groupName,String groupBy) {
        this.groupName = groupName;
        this.groupBy = groupBy;
    }

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        return doExecute(invocation);
    }

    CommandResult doExecute(H5mCommandInvocation invocation) throws InterruptedException {
        if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
        if(groupName==null){
            invocation.println("group name is required (use --from)");
            return CommandResult.FAILURE;
        }
        NodeGroup nodeGroup = nodeGroupService.byName(groupName);
        if(nodeGroup == null){
            invocation.println("NodeEntity group "+groupName+" not found");
            return CommandResult.FAILURE;
        }

        if(groupBy!=null){
            List<Node> foundNodes = nodeService.findNodeByFqdn(groupBy,nodeGroup.id());
            if(foundNodes.isEmpty()){
                invocation.println(groupBy+" not found");
                return CommandResult.FAILURE;
            }else if (foundNodes.size()>1){
                invocation.println(groupBy+" is ambiguous, matched the following nodes:");
                for(int i=0;i<foundNodes.size();i++){
                    invocation.println(String.format("%3d %s",i,foundNodes.get(i).name()));
                }
                return CommandResult.FAILURE;
            }else{
                Node foundNode = foundNodes.get(0);
                List<JqValue> jsons = valueService.getGroupedValues(foundNode.id());
                int totalCount = jsons.size();
                if (limit > 0 && jsons.size() > limit) {
                    jsons = jsons.subList(0, limit);
                }
                if(Format.raw.equals(format)){
                    invocation.println("Count: " + totalCount + (limit > 0 ? " (showing " + jsons.size() + ")" : ""));
                    invocation.println(ListCmd.table(80, jsons,
                            List.of("data"),
                            List.of(JqValue::toJsonString)));
                }else{
                    Set<String> keys = new HashSet<>();
                    for(JqValue json : jsons){
                        if(json instanceof JqObject object){
                            for(String key : object.objectValue().keySet()){
                                if(!key.startsWith("_")){
                                    keys.add(key);
                                }
                            }
                        }
                    }
                    List<String> keyList = new ArrayList<>(keys);
                    keyList.sort(String.CASE_INSENSITIVE_ORDER);
                    List<Function<JqValue,Object>> accessors = keyList.stream().map(name-> (Function<JqValue, Object>) json -> {
                        JqValue found = json.getField(name);
                        if(found.isNull()){
                            return "null";
                        }else if(found instanceof JqString s) {
                            return s.stringValue();
                        }else if (found instanceof JqNumber n) {
                            return n.isIntegral() ? (Object) n.longValue() : n.doubleValue();
                        }else{
                            return found.toJsonString();
                        }
                    }).toList();
                    invocation.println("Count: " + totalCount + (limit > 0 ? " (showing " + jsons.size() + ")" : ""));
                    invocation.println(ListCmd.table(80, jsons, keyList, accessors));
                }

            }
        }else {
            if (Thread.interrupted()) throw new InterruptedException("List values interrupted");
            List<Value> values = valueService.getNodeDescendantValues(nodeGroup.root().id());
            int totalCount = values.size();
            if (limit > 0 && values.size() > limit) {
                values = values.subList(0, limit);
            }
            invocation.println("Count: " + totalCount + (limit > 0 ? " (showing " + values.size() + ")" : ""));
            invocation.println(ListCmd.table(80, values,
                    List.of("id", "data", "node.id"),
                    List.of(v -> v.id(), v -> {
                        JqValue found = v.data();
                        if(found == null || found.isNull()){
                            return "null";
                        }else if(found instanceof JqString s) {
                            return s.stringValue();
                        }else if (found instanceof JqNumber n){
                            return n.isIntegral() ? (Object) n.longValue() : n.doubleValue();
                        }else{
                            return found.toJsonString();
                        }

                    }, v -> v.node().id())));
        }
        return CommandResult.SUCCESS;
    }
}
