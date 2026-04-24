package io.hyperfoil.tools.h5m.api;

import java.util.List;
import java.util.Objects;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A transformation node in the DAG pipeline")
public record Node(
        @Schema(description = "Unique node ID") Long id,
        @Schema(description = "Node name") String name,
        @Schema(description = "Fully qualified domain name") String fqdn,
        @Schema(description = "Node type") NodeType type,
        @Schema(description = "Parent node group") NodeGroup group,
        @Schema(description = "Node operation (jq filter, JS function, etc.)") String operation,
        @Schema(description = "Source dependency nodes") List<Node> sources) {

/*        @Override
        public boolean equals(Object o) {
            if(o instanceof Node n){
                return Objects.equals(n.id, id) &&
                        Objects.equals(n.name,name) &&
                        Objects.equals(n.type,type) &&
                        (n.group!= null && group!=null ? Objects.equals(n.group.id,group.id) : n.group == group)
                        && Objects.equals(n.operation,operation) && Objects.equals(n.sources,sources);
            }
            return false;
        }*/

        @Override
        public int hashCode(){
            return Objects.hash(id,name,type,group!=null ? group.id() : null,operation,sources);
        }

}
