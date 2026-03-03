package io.hyperfoil.tools.h5m.benchmark;

import io.hyperfoil.tools.h5m.entity.node.JqNode;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class NodeDependsOnBenchmark {

    // --- Deep chain benchmarks ---

    @State(Scope.Thread)
    public static class DeepChainState {
        @Param({"10", "100", "1000"})
        int depth;

        JqNode[] chain;
        JqNode leaf;
        JqNode root;
        JqNode middle;

        @Setup(Level.Trial)
        public void setup() {
            GraphBuilder.resetIds();
            chain = GraphBuilder.buildDeepChain(depth);
            root = chain[0];
            leaf = chain[depth - 1];
            middle = chain[depth / 2];
        }
    }

    @Benchmark
    public boolean deepChain_dependsOn_root(DeepChainState state) {
        return state.leaf.dependsOn(state.root);
    }

    @Benchmark
    public boolean deepChain_dependsOn_middle(DeepChainState state) {
        return state.leaf.dependsOn(state.middle);
    }

    @Benchmark
    public boolean deepChain_dependsOn_notFound(DeepChainState state) {
        return state.root.dependsOn(state.leaf);
    }

    // --- Wide fan benchmarks ---

    @State(Scope.Thread)
    public static class WideFanState {
        @Param({"10", "100", "1000"})
        int width;

        JqNode[] fan;
        JqNode root;
        JqNode collector;
        JqNode lastLeaf;

        @Setup(Level.Trial)
        public void setup() {
            GraphBuilder.resetIds();
            fan = GraphBuilder.buildWideFan(width);
            root = fan[0];
            lastLeaf = fan[width];
            // collector depends on all leaves
            collector = new JqNode("collector");
            collector.id = Long.MAX_VALUE;
            for (int i = 1; i <= width; i++) {
                collector.sources.add(fan[i]);
            }
        }
    }

    @Benchmark
    public boolean wideFan_collector_dependsOn_root(WideFanState state) {
        return state.collector.dependsOn(state.root);
    }

    @Benchmark
    public boolean wideFan_collector_dependsOn_lastLeaf(WideFanState state) {
        return state.collector.dependsOn(state.lastLeaf);
    }

    // --- Diamond benchmarks ---

    @State(Scope.Thread)
    public static class DiamondState {
        @Param({"4", "6", "8"})
        int layers;

        @Param({"5", "10"})
        int width;

        JqNode[] diamond;
        JqNode root;
        JqNode sink;

        @Setup(Level.Trial)
        public void setup() {
            GraphBuilder.resetIds();
            diamond = GraphBuilder.buildDiamond(layers, width);
            root = diamond[0];
            sink = diamond[diamond.length - 1];
        }
    }

    @Benchmark
    public boolean diamond_sink_dependsOn_root(DiamondState state) {
        return state.sink.dependsOn(state.root);
    }

    @Benchmark
    public boolean diamond_root_dependsOn_sink(DiamondState state) {
        return state.root.dependsOn(state.sink);
    }
}
