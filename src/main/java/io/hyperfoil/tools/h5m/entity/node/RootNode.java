package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.EphemeralMode;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("root")
public class RootNode extends NodeEntity {

    public RootNode() {
        super();
        super.ephemeral = EphemeralMode.KEEP;

    }

    @Override
    public NodeType type() {
        return NodeType.ROOT;
    }


    public void setEphemeral(EphemeralMode ephemeral){

    }

    //The root node does not shallow copy
    @Override
    protected NodeEntity shallowCopy() {
        return this;
    }
}
