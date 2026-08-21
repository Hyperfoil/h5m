package io.hyperfoil.tools.h5m.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aesh.charts.common.ChartStyle;
import org.aesh.charts.linechart.LineChart;
import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.node.FingerprintNode;
import io.hyperfoil.tools.h5m.entity.node.JqNode;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;
import io.hyperfoil.tools.h5m.entity.node.RootNode;
import io.hyperfoil.tools.h5m.svc.FolderService;
import io.hyperfoil.tools.h5m.svc.ValueService;
import io.hyperfoil.tools.jjq.value.JqNumber;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies change detection markers land on the chart in both domain and
 * upload-order modes by asserting marker labels in the rendered output.
 */
@QuarkusTest
public class ChartMarkersTest extends FreshDb {

    @Inject
    TransactionManager tm;

    @Inject
    FolderService folderService;

    @Inject
    NodeServiceInterface nodeService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    FolderServiceInterface folderServiceApi;

    @Inject
    ValueServiceInterface valueServiceApi;

    @Inject
    ValueService valueService;

    private ChartCmd chartCmd() {
        ChartCmd cmd = new ChartCmd();
        cmd.nodeService = nodeService;
        cmd.nodeGroupService = nodeGroupService;
        cmd.folderService = folderServiceApi;
        cmd.valueService = valueServiceApi;
        cmd.valueServiceImpl = valueService;
        cmd.style = ChartStyle.BRAILLE;
        cmd.chartWidth = 80;
        cmd.chartHeight = 20;
        return cmd;
    }

    private long setupFolder(String name, double[][] uploads) throws Exception {
        tm.begin();
        long folderId = folderService.create(name).id();
        FolderEntity folder = folderService.read(folderId);
        NodeEntity rangeNode = new JqNode("range", ".y", folder.group.root);
        rangeNode.group = folder.group;
        rangeNode.persist();
        NodeEntity domainNode = new JqNode("domain", ".d", folder.group.root);
        domainNode.group = folder.group;
        domainNode.persist();
        NodeEntity fpNode = new FingerprintNode("fp", "", List.of(folder.group.root));
        fpNode.group = folder.group;
        fpNode.persist();

        List<ValueEntity> roots = new ArrayList<>();
        for (double[] u : uploads) {
            ValueEntity root = new ValueEntity(null, folder.group.root, JqValues.parse("{}"));
            root.persist();
            roots.add(root);
            new ValueEntity(null, rangeNode, JqNumber.of(u[1]), List.of(root)).persist();
            new ValueEntity(null, domainNode, JqNumber.of(u[0]), List.of(root)).persist();
            new ValueEntity(null, fpNode, JqString.of("fp"), List.of(root)).persist();
        }

        // Persisted RD detection on the middle upload with a 900% ratio label
        RelativeDifference rd = new RelativeDifference("rd", "{}");
        rd.setNodes(fpNode, folder.group.root, rangeNode, domainNode);
        rd.group = folder.group;
        rd.persist();
        JqValue changeData = JqObject.builder()
                .put("previous", JqNumber.of(1))
                .put("last", JqNumber.of(10))
                .put("value", JqNumber.of(10))
                .put("ratio", JqNumber.of(900))
                .put("domainvalue", JqNumber.of(20))
                .put("fingerprint", JqString.of("fp"))
                .build();
        new ValueEntity(null, rd, changeData, List.of(roots.get(1))).persist();
        // Second detection on the same upload with a different ratio --
        // co-located markers must stack legibly instead of overwriting
        JqValue changeData2 = JqObject.builder()
                .put("previous", JqNumber.of(1))
                .put("last", JqNumber.of(2))
                .put("value", JqNumber.of(2))
                .put("ratio", JqNumber.of(100))
                .put("domainvalue", JqNumber.of(20))
                .put("fingerprint", JqString.of("fp"))
                .build();
        new ValueEntity(null, rd, changeData2, List.of(roots.get(1))).persist();
        tm.commit();
        return folderId;
    }

    private ChartCmd wiredCommand(String folderName, List<JqValue> rows, boolean withDomain) {
        NodeGroup nodeGroup = nodeGroupService.find(folderName);
        assertNotNull(nodeGroup);
        ChartCmd cmd = chartCmd();
        cmd.rangeNodeName = "range";
        cmd.folderName = folderName;
        cmd.allValues = rows;
        cmd.rangeNode = nodeService.findNodeByFqdn("range", nodeGroup.id()).getFirst();
        cmd.fingerprintNode = nodeService.findNodeByFqdn("fp", nodeGroup.id()).getFirst();
        if (withDomain) {
            cmd.domainNodeName = "domain";
            cmd.domainNode = nodeService.findNodeByFqdn("domain", nodeGroup.id()).getFirst();
        }
        cmd.nodeGroup = nodeGroup;
        cmd.distinctFingerprints = List.of("\"fp\"");
        cmd.selectedFingerprints = List.of("\"fp\"");
        cmd.shortLabels = Map.of("\"fp\"", "fp");
        return cmd;
    }

    @Test
    public void markersRenderInDomainMode() throws Exception {
        long folderId = setupFolder("marker-domain", new double[][]{{10, 1}, {20, 2}, {30, 10}});
        ChartCmd cmd = wiredCommand("marker-domain",
                valueService.getAlignedValues(
                        nodeService.findNodeByFqdn("range", nodeGroupService.find("marker-domain").id()).getFirst().id(),
                        nodeService.findNodeByFqdn("domain", nodeGroupService.find("marker-domain").id()).getFirst().id(),
                        folderId,
                        nodeService.findNodeByFqdn("fp", nodeGroupService.find("marker-domain").id()).getFirst().id()),
                true);
        LineChart chart = cmd.buildChart();
        assertNotNull(chart, "chart should build");
        assertTrue(chart.render().contains("900%"),
                "detection marker label should be rendered in domain mode");
    }

    @Test
    public void coLocatedMarkersBothRender() throws Exception {
        long folderId = setupFolder("marker-stacked", new double[][]{{10, 1}, {20, 2}, {30, 10}});
        ChartCmd cmd = wiredCommand("marker-stacked",
                valueService.getAlignedValues(
                        nodeService.findNodeByFqdn("range", nodeGroupService.find("marker-stacked").id()).getFirst().id(),
                        folderId,
                        nodeService.findNodeByFqdn("fp", nodeGroupService.find("marker-stacked").id()).getFirst().id()),
                false);
        LineChart chart = cmd.buildChart();
        assertNotNull(chart, "chart should build");
        String rendered = chart.render();
        assertTrue(rendered.contains("900%"),
                "first co-located marker label should be rendered");
        assertTrue(rendered.contains("100%"),
                "second co-located marker label should be rendered alongside the first");
    }

    @Test
    public void markersRenderInUploadOrderMode() throws Exception {
        long folderId = setupFolder("marker-upload", new double[][]{{10, 1}, {20, 2}, {30, 10}});
        ChartCmd cmd = wiredCommand("marker-upload",
                valueService.getAlignedValues(
                        nodeService.findNodeByFqdn("range", nodeGroupService.find("marker-upload").id()).getFirst().id(),
                        folderId,
                        nodeService.findNodeByFqdn("fp", nodeGroupService.find("marker-upload").id()).getFirst().id()),
                false);
        assertEquals(3, cmd.allValues.size());
        LineChart chart = cmd.buildChart();
        assertNotNull(chart, "chart should build");
        String rendered = chart.render();
        assertTrue(rendered.contains("900%"),
                "detection marker label should be rendered in upload-order mode");
        assertTrue(rendered.contains("100%"),
                "co-located marker label should be decluttered, not overwritten");
        assertTrue(rendered.contains("RD"),
                "native legend should carry the marker entry");
    }

    @Test
    public void noMarkersForUntargetedMetric() throws Exception {
        long folderId = setupFolder("marker-untargeted", new double[][]{{10, 1}, {20, 2}, {30, 10}});
        ChartCmd cmd = wiredCommand("marker-untargeted",
                valueService.getAlignedValues(
                        nodeService.findNodeByFqdn("range", nodeGroupService.find("marker-untargeted").id()).getFirst().id(),
                        folderId,
                        nodeService.findNodeByFqdn("fp", nodeGroupService.find("marker-untargeted").id()).getFirst().id()),
                false);
        // Chart a metric no detection node targets: the fixture RD node
        // targets "range", so markers must come back empty, not fail.
        cmd.rangeNodeName = "other";
        LineChart chart = cmd.buildChart();
        assertNotNull(chart, "chart should still build without markers");
        assertTrue(cmd.markersEmpty,
                "markersEmpty flag must be set when no detection targets the metric");
    }
}
