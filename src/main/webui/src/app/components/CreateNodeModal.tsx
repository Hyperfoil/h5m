import type { Direction, Filter, Node as ApiNode, NodeType } from '@client/types.gen.ts';

import { extractErrorMessage } from '@app/context/NotificationProvider.tsx';
import { useNotification } from '@app/context/useNotification.tsx';
import { fieldError } from '@app/validation.ts';
import {
  Button,
  ComposedModal,
  Form,
  InlineNotification,
  ModalBody,
  ModalFooter,
  ModalHeader,
  MultiSelect,
  ProgressIndicator,
  ProgressStep,
  Select,
  SelectItem,
  SelectItemGroup,
  Stack,
  TextArea,
  TextInput,
} from '@carbon/react';
import { byIdOptions, createConfiguredMutation, createNodeMutation } from '@client/@tanstack/react-query.gen.ts';
import { zCreateNodeQuery } from '@client/zod.gen.ts';
import { useForm, useSelector } from '@tanstack/react-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { z } from 'zod';

import { DETECTION_SOURCES, DetectionConfigStep, buildConfig } from './DetectionConfigStep';

interface CreateNodeModalProps {
  open: boolean;
  onClose: () => void;
  groupId: number;
}

type StepKey = 'type' | 'sources' | 'operation' | 'detection';

const STEP_LABEL: Record<StepKey, string> = {
  type: 'Node type',
  sources: 'Sources',
  operation: 'Operation',
  detection: 'Detection',
};

const NODE_TYPES: { category: string; value: NodeType; label: string; extraSteps: StepKey[]; placeholder?: string; helperText?: string }[] = [
  {
    category: 'Extraction',
    value: 'JQ',
    label: 'JQ',
    extraSteps: ['sources', 'operation'],
    placeholder: '.cpu',
    helperText: 'Expression applied to the selected source, e.g. .cpu',
  },
  {
    category: 'Extraction',
    value: 'JS',
    label: 'JavaScript',
    extraSteps: ['sources', 'operation'],
    placeholder: '(cpu, memory) => cpu / memory',
    helperText: 'Arrow function — parameter names match source node names, e.g. (cpu, memory) => cpu / memory',
  },
  {
    category: 'Extraction',
    value: 'JSONATA',
    label: 'JSONata',
    extraSteps: ['sources', 'operation'],
    placeholder: 'payload.cpu',
    helperText: 'Expression applied to the selected source, e.g. .cpu',
  },
  { category: 'Extraction', value: 'SPLIT', label: 'Split', extraSteps: ['operation'], placeholder: 'expression' },
  { category: 'Aggregation', value: 'FINGERPRINT', label: 'Fingerprint', extraSteps: ['sources'] },
  { category: 'Detection', value: 'FIXED_THRESHOLD', label: 'Fixed Threshold', extraSteps: ['sources', 'detection'] },
  { category: 'Detection', value: 'RELATIVE_DIFFERENCE', label: 'Relative Difference', extraSteps: ['sources', 'detection'] },
  { category: 'Detection', value: 'STDDEV_ANOMALY', label: 'StdDev Anomaly', extraSteps: ['sources', 'detection'] },
  { category: 'Detection', value: 'EDIVISIVE', label: 'E-Divisive', extraSteps: ['sources', 'detection'] },
];

export interface FormValues {
  name: string;
  type: NodeType;
  operation: string;
  sources: string[];
  fpSources: number[];
  srcFingerprint: string;
  srcGroupBy: string;
  srcRange: string;
  srcDomain: string;
  ftConfig: { min: number | string; max: number | string; minInclusive: boolean; maxInclusive: boolean; fingerprintFilter: string };
  rdConfig: { filter: Filter; threshold: number | string; window: number | string; minPrevious: number | string; fingerprintFilter: string };
  sdConfig: { windowSize: number | string; deviations: number | string; direction: Direction; minDataPoints: number | string; fingerprintFilter: string };
  edConfig: {
    windowLen: number | string;
    maxPvalue: number | string;
    minMagnitude: number | string;
    maxSeriesLength: number | string;
    fingerprintFilter: string;
  };
}

const DEFAULT_VALUES: FormValues = {
  name: '',
  type: 'JQ',
  operation: '',
  sources: [],
  fpSources: [],
  srcFingerprint: '',
  srcGroupBy: '',
  srcRange: '',
  srcDomain: '',
  ftConfig: { min: '', minInclusive: true, max: '', maxInclusive: true, fingerprintFilter: '' },
  rdConfig: { filter: 'MEAN', threshold: 0.2, window: 1, minPrevious: 5, fingerprintFilter: '' },
  sdConfig: { windowSize: 10, deviations: 2.0, direction: 'BOTH', minDataPoints: 5, fingerprintFilter: '' },
  edConfig: { windowLen: 50, maxPvalue: 0.001, minMagnitude: 0.0, maxSeriesLength: 500, fingerprintFilter: '' },
};

export const CreateNodeModal = ({ open, onClose, groupId }: CreateNodeModalProps) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const notifications = useNotification();

  const { data: nodeGroup, isLoading: nodesLoading } = useQuery({
    ...byIdOptions({ path: { id: groupId } }),
    enabled: open,
  });

  const availableNodes: ApiNode[] = [...(nodeGroup?.root ? [nodeGroup.root] : []), ...(nodeGroup?.sources ?? [])];

  const onSuccess = () => {
    void queryClient.invalidateQueries();
    notifications.success('Node created');
    handleClose();
  };
  const onError = (e: unknown) => {
    setSubmitError(extractErrorMessage(e) ?? 'Failed to create node');
  };

  const createNode = useMutation({ ...createNodeMutation(), onSuccess, onError });
  const createConfigured = useMutation({ ...createConfiguredMutation(), onSuccess, onError });

  const form = useForm({
    defaultValues: DEFAULT_VALUES,
    onSubmit: ({ value }) => {
      setSubmitError(null);
      const name = value.name.trim();
      const type = value.type;

      if (steps.includes('detection')) {
        const sources = [Number(value.srcFingerprint), Number(value.srcGroupBy), Number(value.srcRange)];
        if (type !== 'FIXED_THRESHOLD' && value.srcDomain) sources.push(Number(value.srcDomain));
        const config = buildConfig(value);
        if (!config) return;
        createConfigured.mutate({ query: { name, groupId, type, sources }, body: config });
      } else if (type === 'FINGERPRINT') {
        createConfigured.mutate({ query: { name, groupId, type: 'FINGERPRINT', sources: value.fpSources } });
      } else {
        createNode.mutate({ query: { name, groupId, type, operation: value.operation.trim() } });
      }
    },
  });

  const nodeType = useSelector(form.store, (s) => s.values.type);

  const handleClose = () => {
    form.reset();
    setCurrentStep(0);
    setSubmitError(null);
    onClose();
  };

  const resetDependentFields = (newType: NodeType) => {
    const name = form.getFieldValue('name');
    form.reset();
    form.setFieldValue('name', name);
    form.setFieldValue('type', newType);
  };

  const setSourcePrefix = (names: string[]) => {
    const operation = form.getFieldValue('operation');
    if (nodeType === 'JS') {
      const bodyMatch = /=>\s*([\s\S]*)$/.exec(operation);
      const body = bodyMatch ? (bodyMatch[1] ?? '').trim() : '';
      form.setFieldValue('operation', names.length > 0 ? `(${names.join(', ')}) => ${body}` : body);
    } else {
      const stripped = operation.replace(/^\{[^}]+}:/, '');
      form.setFieldValue('operation', names.length > 0 ? `{${names.join(',')}}:${stripped}` : stripped);
    }
  };

  const handleSourceSelect = (nodeId: string) => {
    form.setFieldValue('sources', nodeId ? [nodeId] : []);
    if (!nodeId) {
      setSourcePrefix([]);
      return;
    }
    const selected = availableNodes.find((n) => String(n.id) === nodeId);
    if (selected?.name) setSourcePrefix([selected.name]);
  };

  const nodeEntry = NODE_TYPES.find((t) => t.value === nodeType);
  const steps: StepKey[] = ['type', ...(nodeEntry?.extraSteps ?? [])];

  const handleNext = () => {
    void form.validateAllFields('submit').then(() => {
      if (form.state.isFieldsValid) {
        setCurrentStep((s) => s + 1);
      }
    });
  };

  return (
    <ComposedModal open={open} onClose={handleClose} size="lg">
      <ModalHeader title="Create Node" />
      <ModalBody>
        <Form
          onSubmit={(e) => {
            e.preventDefault();
          }}
        >
          <Stack gap={7}>
            <ProgressIndicator
              currentIndex={currentStep}
              onChange={(stepIndex: number) => {
                if (stepIndex <= currentStep) {
                  setCurrentStep(stepIndex);
                  return;
                }
                void form.validateAllFields('submit').then(() => {
                  if (form.state.isFieldsValid) {
                    setCurrentStep(stepIndex);
                  }
                });
              }}
              spaceEqually
            >
              {steps.map((s) => (
                <ProgressStep key={s} label={STEP_LABEL[s]} />
              ))}
            </ProgressIndicator>

            {steps[currentStep] === 'type' && (
              <Stack gap={6}>
                <form.Field
                  name="name"
                  validators={{
                    onBlur: zCreateNodeQuery.shape.name,
                    onSubmit: zCreateNodeQuery.shape.name,
                  }}
                >
                  {(field) => (
                    <TextInput
                      id="node-name"
                      labelText="Name (required)"
                      placeholder="e.g. cpu"
                      value={field.state.value}
                      onChange={(e) => {
                        field.handleChange(e.target.value);
                      }}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>

                <form.Field name="type">
                  {(field) => (
                    <Select
                      id="node-type"
                      labelText="Type"
                      value={field.state.value}
                      onChange={(e) => {
                        const newType = e.target.value as NodeType;
                        field.handleChange(newType);
                        resetDependentFields(newType);
                      }}
                    >
                      {[...new Set(NODE_TYPES.map((t) => t.category))].map((category) => (
                        <SelectItemGroup key={category} label={category}>
                          {NODE_TYPES.filter((t) => t.category === category).map((t) => (
                            <SelectItem key={t.value} value={t.value} text={t.label} />
                          ))}
                        </SelectItemGroup>
                      ))}
                    </Select>
                  )}
                </form.Field>
              </Stack>
            )}

            {steps[currentStep] === 'sources' && (
              <Stack gap={6}>
                {(nodeType === 'JQ' || nodeType === 'JS') && (
                  <form.Field name="sources">
                    {(field) => {
                      const sourceItems = availableNodes
                        .filter((n) => n.type !== 'ROOT')
                        .map((n) => ({ id: String(n.id), label: `${n.name ?? '?'} (${n.type ?? '?'})`, name: n.name ?? '' }));
                      return (
                        <MultiSelect
                          key={nodeType}
                          id="extraction-sources"
                          titleText="Source nodes (optional)"
                          label="Select source nodes"
                          disabled={nodesLoading}
                          items={sourceItems}
                          itemToString={(item) => item.label}
                          initialSelectedItems={sourceItems.filter((item) => field.state.value.includes(item.id))}
                          onChange={({ selectedItems }) => {
                            const items = selectedItems ?? [];
                            field.handleChange(items.map((i) => i.id));
                            setSourcePrefix(items.map((i) => i.name));
                          }}
                        />
                      );
                    }}
                  </form.Field>
                )}

                {nodeType === 'JSONATA' && (
                  <form.Field name="sources">
                    {(field) => (
                      <Select
                        id="node-source"
                        labelText="Source node (optional)"
                        value={field.state.value[0] ?? ''}
                        onChange={(e) => {
                          handleSourceSelect(e.target.value);
                        }}
                        disabled={nodesLoading}
                      >
                        <SelectItem value="" text="Root (no parent source)" />
                        {availableNodes.map((n) => (
                          <SelectItem key={n.id} value={String(n.id)} text={`${n.name ?? '?'} (${n.type ?? '?'})`} />
                        ))}
                      </Select>
                    )}
                  </form.Field>
                )}

                {nodeType === 'FINGERPRINT' && (
                  <form.Field
                    name="fpSources"
                    // workaround for hey-api/hey-api#1802: Zod plugin generates bigint for int64 despite bigInt:false
                    validators={{ onSubmit: z.array(z.number()).min(1) }}
                  >
                    {(field) => (
                      <MultiSelect
                        id="fp-sources"
                        titleText="Source nodes"
                        label="Select source nodes"
                        disabled={nodesLoading}
                        items={availableNodes
                          .filter((n): n is ApiNode & { id: number } => n.id !== undefined)
                          .map((n) => ({ id: String(n.id), label: `${n.name ?? '?'} (${n.type ?? '?'})`, nodeId: n.id }))}
                        itemToString={(item) => item.label}
                        invalid={field.state.meta.errors.length > 0}
                        invalidText={fieldError(field.state.meta.errors)}
                        onChange={({ selectedItems }) => {
                          field.handleChange((selectedItems ?? []).map((i) => i.nodeId));
                        }}
                      />
                    )}
                  </form.Field>
                )}

                {steps.includes('detection') && (
                  <Stack gap={6}>
                    {DETECTION_SOURCES.map((src) => (
                      <form.Field key={src.name} name={src.name} validators={{ onSubmit: src.schema }}>
                        {(field) => (
                          <Select
                            id={src.name}
                            labelText={src.label}
                            value={field.state.value}
                            onChange={(e) => {
                              field.handleChange(e.target.value);
                            }}
                            disabled={nodesLoading}
                            invalid={field.state.meta.errors.length > 0}
                            invalidText={fieldError(field.state.meta.errors)}
                          >
                            <SelectItem value="" text={`Select ${src.label.toLowerCase()}`} />
                            {availableNodes
                              .filter((n) => n.type && src.allowedTypes.includes(n.type))
                              .map((n) => (
                                <SelectItem key={n.id} value={String(n.id)} text={`${n.name ?? '?'} (${n.type ?? '?'})`} />
                              ))}
                          </Select>
                        )}
                      </form.Field>
                    ))}
                    {nodeType !== 'FIXED_THRESHOLD' && (
                      <form.Field name="srcDomain">
                        {(field) => (
                          <Select
                            id="src-domain"
                            labelText="Domain node (optional)"
                            value={field.state.value}
                            onChange={(e) => {
                              field.handleChange(e.target.value);
                            }}
                            disabled={nodesLoading}
                          >
                            <SelectItem value="" text="None" />
                            {availableNodes
                              .filter((n) => n.type && NODE_TYPES.filter((type) => type.category == 'Extraction').find((type) => type.value === n.type))
                              .map((n) => (
                                <SelectItem key={n.id} value={String(n.id)} text={`${n.name ?? '?'} (${n.type ?? '?'})`} />
                              ))}
                          </Select>
                        )}
                      </form.Field>
                    )}
                  </Stack>
                )}
              </Stack>
            )}

            {steps[currentStep] === 'operation' && (
              <form.Field name="operation" validators={{ onSubmit: zCreateNodeQuery.shape.operation }}>
                {(field) => (
                  <TextArea
                    id="node-operation"
                    labelText="Operation"
                    rows={4}
                    placeholder={nodeEntry?.placeholder}
                    helperText={nodeEntry?.helperText}
                    value={field.state.value}
                    onChange={(e) => {
                      field.handleChange(e.target.value);
                    }}
                    className="type-code"
                    invalid={field.state.meta.errors.length > 0}
                    invalidText={fieldError(field.state.meta.errors)}
                  />
                )}
              </form.Field>
            )}

            {steps[currentStep] === 'detection' && <DetectionConfigStep form={form} nodeType={nodeType} />}
            {submitError && (
              <InlineNotification
                kind="error"
                lowContrast
                title="Failed to create node"
                subtitle={submitError}
                onCloseButtonClick={() => {
                  setSubmitError(null);
                }}
              />
            )}
          </Stack>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={handleClose}>
          Cancel
        </Button>
        {currentStep > 0 && (
          <Button
            kind="secondary"
            onClick={() => {
              setCurrentStep((s) => s - 1);
            }}
          >
            Back
          </Button>
        )}
        {currentStep === steps.length - 1 ? (
          <Button
            kind="primary"
            disabled={createNode.isPending || createConfigured.isPending}
            onClick={() => {
              void form.handleSubmit();
            }}
          >
            {createNode.isPending || createConfigured.isPending ? 'Saving...' : 'Save'}
          </Button>
        ) : (
          <Button kind="primary" onClick={handleNext}>
            Next
          </Button>
        )}
      </ModalFooter>
    </ComposedModal>
  );
};
