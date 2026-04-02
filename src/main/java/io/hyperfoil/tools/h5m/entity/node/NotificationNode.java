package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.yaup.json.Json;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("notif")
public class NotificationNode extends NodeEntity {

    @Transient
    private Json config;

    public NotificationNode() {
        config = new Json();
    }

    public NotificationNode(String name, String operation) {
        super(name, operation);
        config = new Json();
    }

    @PostLoad
    public void loadConfig() {
        if (this.config == null || this.config.isEmpty()) {
            if (this.operation != null && !this.operation.isBlank()) {
                config = Json.fromString(this.operation);
            } else {
                config = new Json();
            }
        }
    }

    public void setMonitoredNodes(List<NodeEntity> monitoredNodes) {
        this.sources = new ArrayList<>(monitoredNodes);
    }

    @Transient
    public List<NodeEntity> getMonitoredNodes() {
        return sources;
    }

    @Override
    public NodeType type() {
        return NodeType.NOTIFICATION;
    }

    @Override
    protected NodeEntity shallowCopy() {
        return new NotificationNode(name, operation);
    }
}
