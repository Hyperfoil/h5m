import type { Node as ApiNode, View, ViewComponent } from '@client/types.gen.ts';

import {
  Button,
  ComposedModal,
  FilterableMultiSelect,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextInput,
} from '@carbon/react';
import { byIdOptions } from '@client/@tanstack/react-query.gen.ts';
import { ViewService } from '@client/sdk.gen.ts';
import { useMutation, useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useState } from 'react';

interface ViewConfigModalProps {
  open: boolean;
  onClose: () => void;
  folderName: string;
  groupId: number;
  view?: View | null;
}

interface NodeItem {
  id: string;
  text: string;
  nodeId: number;
}

export const ViewConfigModal = ({ open, onClose, folderName, groupId, view }: ViewConfigModalProps) => {
  const queryClient = useQueryClient();
  const { data: nodeGroup } = useSuspenseQuery(byIdOptions({ path: { id: groupId } }));
  const isEditing = view != null && view.id != null;
  const isDefault = view?.name === 'Default';

  const [viewName, setViewName] = useState(view?.name ?? '');
  const [selectedNodes, setSelectedNodes] = useState<NodeItem[]>([]);

  // Initialize from existing view when editing
  useEffect(() => {
    if (view) {
      setViewName(view.name);
      const items = (view.components ?? []).map((c: ViewComponent) => ({
        id: String(c.nodeId),
        text: c.headerName ?? c.nodeName ?? '',
        nodeId: c.nodeId!,
      }));
      setSelectedNodes(items);
    } else {
      setViewName('');
      setSelectedNodes([]);
    }
  }, [view]);

  // Available nodes for the multi-select (exclude detection nodes)
  const availableNodes: NodeItem[] = (nodeGroup.sources ?? [])
    .filter((n: ApiNode) => !['FIXED_THRESHOLD', 'RELATIVE_DIFFERENCE', 'EDIVISIVE'].includes(n.type ?? ''))
    .map((n: ApiNode) => ({
      id: String(n.id),
      text: n.name ?? '',
      nodeId: n.id!,
    }));

  const createMutation = useMutation({
    mutationFn: (data: View) =>
      ViewService.createView({
        path: { name: folderName },
        body: data,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['getViews'] });
      onClose();
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: View) =>
      ViewService.updateView({
        path: { name: folderName, viewId: view!.id! },
        body: data,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['getViews'] });
      onClose();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () =>
      ViewService.deleteView({
        path: { name: folderName, viewId: view!.id! },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['getViews'] });
      onClose();
    },
  });

  const handleSave = useCallback(() => {
    const components: ViewComponent[] = selectedNodes.map((node, idx) => ({
      nodeId: node.nodeId,
      headerName: node.text,
      headerOrder: idx,
    }));

    const viewData: View = {
      name: viewName,
      components,
    };

    if (isEditing) {
      updateMutation.mutate(viewData);
    } else {
      createMutation.mutate(viewData);
    }
  }, [viewName, selectedNodes, isEditing, createMutation, updateMutation]);

  const handleDelete = useCallback(() => {
    if (isEditing && !isDefault && window.confirm('Delete this view?')) {
      deleteMutation.mutate();
    }
  }, [isEditing, isDefault, deleteMutation]);

  const isSaving = createMutation.isPending || updateMutation.isPending;
  const canSave = viewName.trim().length > 0 && selectedNodes.length > 0;

  return (
    <ComposedModal open={open} onClose={onClose} size="lg">
      <ModalHeader title={isEditing ? `Edit View: ${view?.name}` : 'Create View'} />
      <ModalBody style={{ minHeight: '24rem', overflow: 'visible' }}>
        <TextInput
          id="view-name"
          labelText="View name"
          value={viewName}
          onChange={(e) => setViewName(e.target.value)}
          disabled={isDefault}
          style={{ marginBottom: 'var(--cds-spacing-05)' }}
        />
        <FilterableMultiSelect
          id="node-selector"
          titleText="Select nodes to display as columns"
          items={availableNodes}
          itemToString={(item: NodeItem) => item?.text ?? ''}
          initialSelectedItems={selectedNodes}
          onChange={({ selectedItems }: { selectedItems: NodeItem[] }) => {
            setSelectedNodes(selectedItems);
          }}
        />
      </ModalBody>
      <ModalFooter>
        {isEditing && !isDefault && (
          <Button kind="danger" onClick={handleDelete} disabled={deleteMutation.isPending}>
            Delete
          </Button>
        )}
        <Button kind="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button kind="primary" onClick={handleSave} disabled={!canSave || isSaving}>
          {isSaving ? 'Saving...' : 'Save'}
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};
