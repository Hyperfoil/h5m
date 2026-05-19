import type { Node, NodeGroup } from '@client/types.gen.ts';

import { buildGraph, collectNodes } from '@app/components/NodeGraphVisualizer';
import { describe, expect, it } from 'vitest';

describe('collectNodes', () => {
  it('collects a single node', () => {
    const map = new Map();
    const node: Node = { id: 1, name: 'root', type: 'ROOT' };
    collectNodes(node, map);
    expect(map.size).toBe(1);
    expect(map.get('1')?.name).toBe('root');
  });

  it('collects node tree recursively', () => {
    const map = new Map();
    const root: Node = {
      id: 1, name: 'root', type: 'ROOT',
      sources: [],
    };
    const child: Node = {
      id: 2, name: 'cpu', type: 'JQ', operation: '.cpu',
      sources: [root],
    };
    collectNodes(child, map);
    expect(map.size).toBe(2);
    expect(map.has('1')).toBe(true);
    expect(map.has('2')).toBe(true);
  });

  it('deduplicates nodes by id', () => {
    const map = new Map();
    const root: Node = { id: 1, name: 'root', type: 'ROOT' };
    collectNodes(root, map);
    collectNodes(root, map);
    expect(map.size).toBe(1);
  });

  it('skips nodes with null id', () => {
    const map = new Map();
    const node: Node = { name: 'orphan', type: 'JQ' };
    collectNodes(node, map);
    expect(map.size).toBe(0);
  });
});

describe('buildGraph', () => {
  it('builds graph from simple node group', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const cpu: Node = { id: 2, name: 'cpu', type: 'JQ', operation: '.cpu', sources: [root] };
    const mem: Node = { id: 3, name: 'mem', type: 'JQ', operation: '.mem', sources: [root] };

    const nodeGroup: NodeGroup = {
      id: 1,
      name: 'test-group',
      root: root,
      sources: [cpu, mem],
    };

    const { nodes, edges } = buildGraph(nodeGroup);

    expect(nodes).toHaveLength(3);
    expect(edges).toHaveLength(2);

    // Verify all nodes are present
    const nodeIds = nodes.map((n) => n.id);
    expect(nodeIds).toContain('1');
    expect(nodeIds).toContain('2');
    expect(nodeIds).toContain('3');

    // Verify edges connect sources to children
    const edgePairs = edges.map((e) => `${e.source}->${e.target}`);
    expect(edgePairs).toContain('1->2');
    expect(edgePairs).toContain('1->3');
  });

  it('handles chained nodes (root -> a -> b)', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const a: Node = { id: 2, name: 'a', type: 'JQ', operation: '.a', sources: [root] };
    const b: Node = { id: 3, name: 'b', type: 'JQ', operation: '.b', sources: [a] };

    const nodeGroup: NodeGroup = {
      id: 1, name: 'chain',
      root: root,
      sources: [a, b],
    };

    const { nodes, edges } = buildGraph(nodeGroup);

    expect(nodes).toHaveLength(3);
    expect(edges).toHaveLength(2);

    const edgePairs = edges.map((e) => `${e.source}->${e.target}`);
    expect(edgePairs).toContain('1->2');
    expect(edgePairs).toContain('2->3');
  });

  it('handles diamond pattern (root -> a, root -> b, a + b -> c)', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const a: Node = { id: 2, name: 'a', type: 'JQ', operation: '.a', sources: [root] };
    const b: Node = { id: 3, name: 'b', type: 'JQ', operation: '.b', sources: [root] };
    const c: Node = { id: 4, name: 'c', type: 'JS', operation: '(a, b) => a + b', sources: [a, b] };

    const nodeGroup: NodeGroup = {
      id: 1, name: 'diamond',
      root: root,
      sources: [a, b, c],
    };

    const { nodes, edges } = buildGraph(nodeGroup);

    expect(nodes).toHaveLength(4);
    expect(edges).toHaveLength(4); // root->a, root->b, a->c, b->c

    const edgePairs = edges.map((e) => `${e.source}->${e.target}`);
    expect(edgePairs).toContain('1->2');
    expect(edgePairs).toContain('1->3');
    expect(edgePairs).toContain('2->4');
    expect(edgePairs).toContain('3->4');
  });

  it('returns empty for node group with no nodes', () => {
    const nodeGroup: NodeGroup = { id: 1, name: 'empty', sources: [] };
    const { nodes, edges } = buildGraph(nodeGroup);
    expect(nodes).toHaveLength(0);
    expect(edges).toHaveLength(0);
  });

  it('assigns correct node types for display', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const jq: Node = { id: 2, name: 'cpu', type: 'JQ', operation: '.cpu', sources: [root] };
    const ft: Node = { id: 3, name: 'alert', type: 'FIXED_THRESHOLD', operation: '{}', sources: [jq] };

    const nodeGroup: NodeGroup = {
      id: 1, name: 'types',
      root: root,
      sources: [jq, ft],
    };

    const { nodes } = buildGraph(nodeGroup);

    const rootNode = nodes.find((n) => n.id === '1');
    const jqNode = nodes.find((n) => n.id === '2');
    const ftNode = nodes.find((n) => n.id === '3');

    expect(rootNode?.data.nodeType).toBe('ROOT');
    expect(jqNode?.data.nodeType).toBe('JQ');
    expect(ftNode?.data.nodeType).toBe('FIXED_THRESHOLD');
    expect(jqNode?.data.operation).toBe('.cpu');
  });

  it('all nodes have positions after layout', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const a: Node = { id: 2, name: 'a', type: 'JQ', operation: '.a', sources: [root] };

    const nodeGroup: NodeGroup = {
      id: 1, name: 'layout',
      root: root,
      sources: [a],
    };

    const { nodes } = buildGraph(nodeGroup);

    for (const node of nodes) {
      expect(node.position).toBeDefined();
      expect(typeof node.position.x).toBe('number');
      expect(typeof node.position.y).toBe('number');
      expect(Number.isNaN(node.position.x)).toBe(false);
      expect(Number.isNaN(node.position.y)).toBe(false);
    }
  });

  it('does not create duplicate edges', () => {
    const root: Node = { id: 1, name: 'root', type: 'ROOT', sources: [] };
    const a: Node = { id: 2, name: 'a', type: 'JQ', operation: '.a', sources: [root] };

    const nodeGroup: NodeGroup = {
      id: 1, name: 'dedup',
      root: root,
      // a appears in both root's traversal and sources list
      sources: [a, a],
    };

    const { edges } = buildGraph(nodeGroup);

    const edgeIds = edges.map((e) => e.id);
    const unique = [...new Set(edgeIds)];
    expect(edgeIds.length).toBe(unique.length);
  });
});
