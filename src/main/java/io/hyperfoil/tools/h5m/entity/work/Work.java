package io.hyperfoil.tools.h5m.entity.work;

import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.node.EDivisive;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;
import io.hyperfoil.tools.h5m.entity.node.StdDevAnomaly;
import io.hyperfoil.tools.h5m.svc.WorkService;
import jakarta.enterprise.inject.spi.CDI;

import java.util.*;
import java.util.stream.Collectors;


//cross test comparison could use sourceNodes and not have an activeNode?
//custom post nodegroup actions could have sourceNodes without activeNode
public class Work implements Runnable, Comparable<Work>{

    private List<Long> sourceValueIds;//IDs of source values — full entities are loaded in WorkService.execute()

    private List<NodeEntity> sourceNodes; //what is going to use a list of sources that are not already listed for the activeNode?

    private int retryCount;

    private Set<NodeEntity> activeNodes;

    /*
     * If the work should be performed after work for any dependent Nodes regardless of Values
     */
    private boolean cumulative = false;

    /*
     * Whether external notifications should be dispatched for detection results.
     * Set to false for recalculations and bulk imports.
     */
    private boolean dispatch = true;

    /*
     * If the work should queue activeNode child values if it creates new values
     */
    private boolean cascade = true;

    public Work(){
        retryCount = 0;
    }
    public Work(NodeEntity activeNode,List<NodeEntity> sourceNodes,List<Long> sourceValueIds){
        this(Set.of(activeNode),sourceNodes,sourceValueIds);
    }
    public Work(Set<NodeEntity> activeNodes,List<NodeEntity> sourceNodes,List<Long> sourceValueIds){
        this();
        this.activeNodes = new HashSet<>(activeNodes); //so that it will be mutable
        if(activeNodes.stream().anyMatch(node -> node instanceof StdDevAnomaly || node instanceof EDivisive)){
            this.cumulative = true;
        }
        this.sourceValueIds = sourceValueIds == null ? Collections.emptyList() : new ArrayList<>(sourceValueIds);
        this.sourceNodes = sourceNodes == null ? Collections.emptyList() : new ArrayList<>(sourceNodes);
    }

    public Set<NodeEntity> getActiveNodes() {
        return activeNodes;
    }

    public void setActiveNodes(Set<NodeEntity> activeNodes) {
        this.activeNodes = activeNodes;
        if(activeNodes.stream().anyMatch(node -> node instanceof StdDevAnomaly || node instanceof EDivisive)){
            this.cumulative = true;
        }else{
            this.cumulative = false;
        }
    }
    public List<Long> getSourceValueIds(){return sourceValueIds;}

    //work A depends on work B if A.activeNode depends on B.activeNode
    public boolean dependsOn(Work work){

        if(work == null || work.activeNodes == null || work.activeNodes.isEmpty()){
            return false;
        }
        if (this.activeNodes == null || this.activeNodes.isEmpty()) {
            return false;
        }
        // Check node-level dependency: does any of this Work's active nodes
        // depend on any of the other Work's active nodes?
        boolean hasNodeDependency = false;
        for (NodeEntity thisNode : this.activeNodes) {
            for (NodeEntity otherNode : work.activeNodes) {
                if (thisNode.dependsOn(otherNode)) {
                    hasNodeDependency = true;
                    break;
                }
            }
            if (hasNodeDependency) break;
        }
        if (!hasNodeDependency) {
            return false;
        }
        if (cumulative || sourceValueIds.isEmpty()) {
            return true;
        }
        for (int i = 0, size = sourceValueIds.size(); i < size; i++) {
            if (work.sourceValueIds.contains(sourceValueIds.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * filters works for any Work that are in the dependsOn path for this
     * @param works
     * @return
     */
    public List<Work> getAncestorWorks(List<Work> works){
        List<Work> rtrn = new ArrayList<>();
        for (Work w : works) {
            if (this.dependsOn(w)) {
                rtrn.add(w);
            }
        }
        return rtrn;
    }


    @Override
    public boolean equals(Object o){
        if(o instanceof Work work){
            if (this.activeNodes == null || work.activeNodes == null
                    || this.sourceNodes == null || work.sourceNodes == null) {
                return false;
            }
            boolean sameNodes =  this.activeNodes.containsAll(work.activeNodes) && work.activeNodes.containsAll(this.activeNodes);
            if(!sameNodes){
                return false;
            }
            if (this.sourceNodes.size() != work.sourceNodes.size()) {
                return false;
            }
            for (int i = 0; i < sourceNodes.size(); i++) {
                if (!sourceNodes.get(i).equals(work.sourceNodes.get(i))) {
                    return false;
                }
            }
            if(cumulative){
                return true;
            }
            if (this.sourceValueIds == null || work.sourceValueIds == null) {
                return false;
            }
            if (this.sourceValueIds.size() != work.sourceValueIds.size()) {
                return false;
            }
            for (int i = 0; i < sourceValueIds.size(); i++) {
                if (!sourceValueIds.get(i).equals(work.sourceValueIds.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    @Override
    public int hashCode(){
        if (activeNodes == null) {
            return 0;
        }
        List<Object> param = new ArrayList<>();
        param.addAll(activeNodes);
        if (sourceNodes != null) {
            param.addAll(sourceNodes);
        }
        if(!cumulative && sourceValueIds != null) {
            param.addAll(sourceValueIds);
        }
        return Objects.hash(param);
    }

    @Override
    public int compareTo(Work o) {
        if(this.dependsOn(o)){
            return 1;
        } else if (o.dependsOn(this)){
            return -1;
        } else {
            return 0;
        }
    }

    @Override public void run() {
        CDI.current().select(WorkService.class).get().execute(this);
    }

    public void releaseReferences() {
        sourceValueIds = null;
        sourceNodes = null;
        activeNodes = null;
    }

    @Override
    public String toString() {
        return "Work<activeNodes="+activeNodes+
                " sourceNodes="+(sourceNodes == null ? "null" : sourceNodes.stream().map(n->""+n.getId()).collect(Collectors.joining(",")))+
                " sourceValueIds="+(sourceValueIds == null ? "null" : sourceValueIds.stream().map(String::valueOf).collect(Collectors.joining(",")))+
                " retry="+retryCount+
                " hashCode="+hashCode()+" >";
    }

    public boolean isCascade() { return cascade; }
    public void setCascade(boolean cascade) { this.cascade = cascade; }

    public boolean isDispatch() { return dispatch; }
    public void setDispatch(boolean dispatch) { this.dispatch = dispatch; }

    public boolean isCumulative() { return cumulative; }
    public void setCumulative(boolean cumulative) { this.cumulative = cumulative; }


    public int getRetryCount() { return retryCount; }
    public void incrementRetryCount(){ this.retryCount++; }
}
