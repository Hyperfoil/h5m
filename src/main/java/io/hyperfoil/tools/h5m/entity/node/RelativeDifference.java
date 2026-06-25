package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.jjq.value.*;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("rd")
public class RelativeDifference extends NodeEntity implements DetectionNode {

    private static final String THRESHOLD = "threshold";
    public static final double DEFAULT_THRESHOLD = 0.2;
    private static final String WINDOW = "window";
    public static final int DEFAULT_WINDOW = 1;
    private static final String MIN_PREVIOUS = "minPrevious";
    public static final int DEFAULT_MIN_PREVIOUS = 5;
    private static final String FILTER =  "filter";
    public static final String DEFAULT_FILTER = "mean";//attribute value must be a constant
    private static final String FINGERPRINT_FILTER = "fingerprintFilter";

    @Transient
    private JqObject config;

    public RelativeDifference() {
        config = JqObject.EMPTY;
    }

    public RelativeDifference(String name, String operation) {
        super(name,operation);
        config = JqObject.EMPTY;
    }

    @Override
    public NodeType type() {
        return NodeType.RELATIVE_DIFFERENCE;
    }

    @PostLoad
    public void loadConfig(){
        if(config == null || config.size() == 0){
            if(this.operation!=null && !this.operation.isBlank()){
                JqValue parsed = JqValues.parse(this.operation);
                config = parsed instanceof JqObject obj ? obj : JqObject.EMPTY;
            }else {
                config = JqObject.EMPTY;
            }
        }
    }

    public void setNodes(NodeEntity fingerprint, NodeEntity groupBy, NodeEntity range, NodeEntity domain){
        List<NodeEntity> sources = new ArrayList<>();
        sources.add(fingerprint);
        sources.add(groupBy);
        sources.add(range);
        if(domain!=null){
            sources.add(domain);
        }
        this.sources = sources;
    }

    @Transient
    public NodeEntity getRangeNode(){
        return sources.get(2);
    }

    //domain node can be null
    @Transient
    public NodeEntity getDomainNode(){
        return sources.size() > 3 ? sources.get(3) : null;
    }

    @Transient
    public NodeEntity getGroupByNode(){return sources.get(1);}

    @Transient
    public NodeEntity getFingerprintNode(){
        return sources.get(0);
    }

    @Transient
    public List<NodeEntity> getFingerprintNodes(){
        return sources.get(0).sources;
    }


    @Transient
    public double getThreshold(){
        return config.get(THRESHOLD).asDouble(DEFAULT_THRESHOLD);
    }
    public void setThreshold(double threshold){
        config = config.with(THRESHOLD, JqNumber.of(threshold));
        operation = config.toJsonString();
    }
    @Transient
    public long getWindow(){
        return config.get(WINDOW).asLong(DEFAULT_WINDOW);
    }
    public void setWindow(long window){
        config = config.with(WINDOW, JqNumber.of(window));
        operation = config.toJsonString();
    }
    @Transient
    public long getMinPrevious(){
        return config.get(MIN_PREVIOUS).asLong(DEFAULT_MIN_PREVIOUS);
    }
    public void setMinPrevious(long minPrevious){
        config = config.with(MIN_PREVIOUS, JqNumber.of(minPrevious));
        operation = config.toJsonString();
    }
    @Transient
    public String getFilter(){
        return config.has(FILTER) ? config.get(FILTER).asString(null) : null;
    }
    public void setFilter(String filter){
        config = config.with(FILTER, JqString.of(filter));
        operation = config.toJsonString();
    }
    @Transient
    public String getFingerprintFilter(){
        return config.has(FINGERPRINT_FILTER) ? config.get(FINGERPRINT_FILTER).asString(null) : null;
    }
    public void setFingerprintFilter(String fingerprintFilter){
        config = config.with(FINGERPRINT_FILTER, JqString.of(fingerprintFilter));
        operation = config.toJsonString();
    }

    @Override
    protected NodeEntity shallowCopy() {
        return new RelativeDifference(name,operation);
    }

}
