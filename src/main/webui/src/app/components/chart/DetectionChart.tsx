import '@carbon/charts-react/styles.css';
import type { ThresholdOptions } from '@carbon/charts';
import type { Node } from '@client/types.gen.ts';

import { ChartTheme, ScaleTypes } from '@carbon/charts';
import { ComboChart } from '@carbon/charts-react';
import { blue50, purple60, red50 } from '@carbon/colors';
import { Column, Dropdown, Grid, InlineLoading, InlineNotification, Stack, Tile } from '@carbon/react';
import { byIdOptions, getLabelValuesOptions } from '@client/@tanstack/react-query.gen.ts';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

interface DetectionChartProps {
  folderId: number;
  groupId: number;
}

const DETECTION_TYPES = new Set([
  'EDIVISIVE',
  'FIXED_THRESHOLD',
  'RELATIVE_DIFFERENCE',
  'STDDEV_ANOMALY',
]);

function parseOperation(operation?: string): Record<string, unknown> {
  if (!operation) return {};
  try {
    const parsed: unknown = JSON.parse(operation);
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export default function DetectionChart({ folderId, groupId }: DetectionChartProps) {
  const { data: nodeGroup, isLoading: isGroupLoading } = useQuery(
    byIdOptions({ path: { id: groupId } }),
  );

  const detectionNodes = useMemo(
    () => (nodeGroup?.sources ?? []).filter((n) => DETECTION_TYPES.has(n.type)),
    [nodeGroup],
  );

  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  const activeNode =
    (selectedNode && detectionNodes.some((n) => n.id === selectedNode.id)
      ? selectedNode
      : detectionNodes[0]) ?? null;

  const rangeNode = activeNode?.sources?.[2];
  const domainNode = activeNode?.sources?.[3];

  const metricNodeId = rangeNode?.id;
  const domainNodeId = domainNode?.id;
  const detectionNodeId = activeNode?.id;

  const nodeIds = useMemo(
    () => [metricNodeId, domainNodeId, detectionNodeId].filter((id): id is number => id != null),
    [metricNodeId, domainNodeId, detectionNodeId],
  );

  const { data: rows = [], isLoading: isDataLoading } = useQuery({
    ...getLabelValuesOptions({
      path: { id: folderId },
      query: { nodeIds, sortById: domainNodeId },
    }),
    enabled: metricNodeId != null && nodeIds.length > 0,
  });

  const rangeName = rangeNode?.name;
  const domainName = domainNode?.name;
  const detectName = activeNode?.name;
  const metricGroup = rangeName ?? 'Metric';

  // Transform data for Carbon Charts
  const chartData = useMemo(() => {
    if (!rangeName) return [];
    const records = rows as unknown as Record<string, unknown>[];

    const metricSeries: { group: string; key: number; value: number }[] = [];
    const changePoints: { group: string; key: number; value: number }[] = [];

    records.forEach((row, i) => {
      const rawMetric = row[rangeName];
      if (rawMetric == null) return;

      const metricVal = Number(rawMetric);
      if (Number.isNaN(metricVal)) return;

      const keyVal =
        domainName && row[domainName] != null ? Number(row[domainName]) : i;
      const key = Number.isNaN(keyVal) ? i : keyVal;

      metricSeries.push({
        group: metricGroup,
        key,
        value: metricVal,
      });


      if (detectName && row[detectName] != null) {
        changePoints.push({
          group: 'Change Point',
          key,
          value: metricVal,
        });
      }
    });

    return [...metricSeries, ...changePoints];
  }, [rows, rangeName, domainName, detectName, metricGroup]);

  // Guidelines / Thresholds
  const thresholds: ThresholdOptions[] = useMemo(() => {
    if (activeNode?.type !== 'FIXED_THRESHOLD') return [];
    const config = parseOperation(activeNode.operation);
    const result: ThresholdOptions[] = [];

    if (config.min != null) {
      result.push({ value: Number(config.min), label: 'Min bound', fillColor: purple60 });
    }
    if (config.max != null) {
      result.push({ value: Number(config.max), label: 'Max bound', fillColor: purple60 });
    }
    return result;
  }, [activeNode]);

  if (isGroupLoading || isDataLoading) {
    return <InlineLoading description="Loading chart data..." />;
  }

  if (detectionNodes.length === 0) {
    return (
      <InlineNotification
        kind="info"
        title="No Detection Nodes"
        subtitle="No change detection nodes (FixedThreshold, RelativeDifference, EDivisive, StdDevAnomaly) were found in this node group."
      />
    );
  }

  const options = {
    title: activeNode?.name ?? 'Metric & Change Detection',
    theme: ChartTheme.G90,
    height: '400px',
    axes: {
      bottom: {
        title: domainNode?.name ?? 'Index',
        mapsTo: 'key',
        scaleType: ScaleTypes.LINEAR,
      },
      left: {
        title: metricGroup,
        mapsTo: 'value',
        scaleType: ScaleTypes.LINEAR,
        thresholds,
      },
    },
    curve: 'curveMonotoneX',
    points: { radius: 4 },
    color: {
      scale: {
        [metricGroup]: blue50,
        'Change Point': red50,
      },
    },
    comboChartTypes: [
      { type: 'line', correspondingDatasets: [metricGroup] },
      { type: 'scatter', correspondingDatasets: ['Change Point'] },
    ],
    
  };

  return (
    <Stack gap={5}>
      <Grid narrow>
        <Column sm={4} md={4} lg={5}>
          <Dropdown
            id="detection-node-selector"
            titleText="Detection check"
            label="Select a detection check"
            items={detectionNodes}
            itemToString={(n: Node | null) => n?.name ?? ''}
            selectedItem={activeNode}
            onChange={({ selectedItem }) => { setSelectedNode(selectedItem); }}
          />
        </Column>
      </Grid>

      <Column lg={10}>
        <Tile>
          {chartData.length > 0 ? (
            <ComboChart key={activeNode?.id ?? 'empty'} data={chartData} options={options} />
          ) : (
            <InlineNotification
              kind="info"
              title="No Data Available"
              subtitle="No data points were found for the selected detection node."
            />
          )}
        </Tile>
      </Column>
    </Stack>
  );
}