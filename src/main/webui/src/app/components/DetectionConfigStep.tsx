import type { EDivisiveConfig, FixedThresholdConfig, NodeConfiguration, NodeType, RelativeDifferenceConfig, StdDevAnomalyConfig } from '@client/types.gen.ts';
import type { FormAsyncValidateOrFn, FormValidateOrFn } from '@tanstack/form-core';
import type { ReactFormExtendedApi } from '@tanstack/react-form';

import { fieldError, groupValidator } from '@app/validation.ts';
import { Checkbox, NumberInput, Select, SelectItem, Stack } from '@carbon/react';
import { zDirection, zEDivisiveConfig, zFilter, zFixedThresholdConfig, zRelativeDifferenceConfig, zStdDevAnomalyConfig } from '@client/zod.gen.ts';
import { z, type ZodType } from 'zod';

import type { FormValues } from './CreateNodeModal';

export const DETECTION_SOURCES: {
  name: 'srcFingerprint' | 'srcGroupBy' | 'srcRange';
  label: string;
  allowedTypes: NodeType[];
  // frontend-only: the API takes a flat sources array, not individual source-role parameters, so per-select validation can't flow from the backend
  schema: ZodType<string, string>;
}[] = [
  { name: 'srcFingerprint', label: 'Fingerprint node', allowedTypes: ['FINGERPRINT'], schema: z.string().min(1) },
  { name: 'srcGroupBy', label: 'GroupBy node', allowedTypes: ['ROOT', 'JQ', 'JS', 'JSONATA', 'SPLIT'], schema: z.string().min(1) },
  { name: 'srcRange', label: 'Range node', allowedTypes: ['JQ', 'JS', 'JSONATA', 'SPLIT'], schema: z.string().min(1) },
];

type NumericConfigField =
  | 'ftConfig.max'
  | 'ftConfig.min'
  | 'rdConfig.minPrevious'
  | 'rdConfig.threshold'
  | 'rdConfig.window'
  | 'sdConfig.deviations'
  | 'sdConfig.minDataPoints'
  | 'sdConfig.windowSize'
  | 'edConfig.maxPvalue'
  | 'edConfig.maxSeriesLength'
  | 'edConfig.minMagnitude'
  | 'edConfig.windowLen';

type DetectionFieldDef =
  | { kind: 'number'; name: NumericConfigField; id: string; label: string; helperText?: string; allowEmpty?: boolean }
  | { kind: 'select'; name: 'rdConfig.filter' | 'sdConfig.direction'; id: string; label: string; options: { value: string; text: string }[] }
  | { kind: 'checkbox'; name: 'ftConfig.minInclusive' | 'ftConfig.maxInclusive'; id: string; label: string };

const DETECTION_FIELDS: Partial<Record<NodeType, DetectionFieldDef[]>> = {
  FIXED_THRESHOLD: [
    { kind: 'number', name: 'ftConfig.min', id: 'ft-min', label: 'Min', allowEmpty: true },
    { kind: 'checkbox', name: 'ftConfig.minInclusive', id: 'ft-min-inclusive', label: 'Min inclusive' },
    { kind: 'number', name: 'ftConfig.max', id: 'ft-max', label: 'Max', allowEmpty: true },
    { kind: 'checkbox', name: 'ftConfig.maxInclusive', id: 'ft-max-inclusive', label: 'Max inclusive' },
  ],
  RELATIVE_DIFFERENCE: [
    {
      kind: 'select',
      name: 'rdConfig.filter',
      id: 'rd-filter',
      label: 'Aggregation filter',
      options: zFilter.options.map((f) => ({ value: f, text: f.charAt(0) + f.slice(1).toLowerCase() })),
    },
    { kind: 'number', name: 'rdConfig.threshold', id: 'rd-threshold', label: 'Threshold (fraction)', helperText: 'e.g. 0.2 = 20%' },
    { kind: 'number', name: 'rdConfig.window', id: 'rd-window', label: 'Window', helperText: 'Recent values to compare' },
    { kind: 'number', name: 'rdConfig.minPrevious', id: 'rd-min-previous', label: 'Min previous', helperText: 'History required' },
  ],
  STDDEV_ANOMALY: [
    { kind: 'number', name: 'sdConfig.windowSize', id: 'sd-window', label: 'Window size' },
    { kind: 'number', name: 'sdConfig.deviations', id: 'sd-deviations', label: 'Deviations' },
    { kind: 'number', name: 'sdConfig.minDataPoints', id: 'sd-min-dp', label: 'Min data points' },
    {
      kind: 'select',
      name: 'sdConfig.direction',
      id: 'sd-direction',
      label: 'Direction',
      options: zDirection.options.map((d) => ({ value: d, text: d.charAt(0) + d.slice(1).toLowerCase() })),
    },
  ],
  EDIVISIVE: [
    { kind: 'number', name: 'edConfig.windowLen', id: 'ed-window', label: 'Window length', helperText: 'Min 3; ≥100 data points recommended' },
    { kind: 'number', name: 'edConfig.maxPvalue', id: 'ed-pvalue', label: 'Max p-value', helperText: 'Significance threshold' },
    { kind: 'number', name: 'edConfig.minMagnitude', id: 'ed-magnitude', label: 'Min magnitude', helperText: 'e.g. 0.1 = 10% change' },
    { kind: 'number', name: 'edConfig.maxSeriesLength', id: 'ed-max-series', label: 'Max series length', helperText: 'Most recent N points to analyze' },
  ],
};

const GROUP_SCHEMAS: Partial<Record<NodeType, ZodType>> = {
  FIXED_THRESHOLD: zFixedThresholdConfig,
  RELATIVE_DIFFERENCE: zRelativeDifferenceConfig,
  STDDEV_ANOMALY: zStdDevAnomalyConfig,
  EDIVISIVE: zEDivisiveConfig,
};

export function buildConfig(v: FormValues): NodeConfiguration | undefined {
  switch (v.type) {
    case 'FIXED_THRESHOLD': {
      const cfg: FixedThresholdConfig = { minInclusive: v.ftConfig.minInclusive, maxInclusive: v.ftConfig.maxInclusive };
      if (v.ftConfig.min !== '') cfg.min = Number(v.ftConfig.min);
      if (v.ftConfig.max !== '') cfg.max = Number(v.ftConfig.max);
      if (v.ftConfig.fingerprintFilter) cfg.fingerprintFilter = v.ftConfig.fingerprintFilter;
      return cfg;
    }
    case 'RELATIVE_DIFFERENCE': {
      const cfg: RelativeDifferenceConfig = {
        filter: v.rdConfig.filter,
        threshold: Number(v.rdConfig.threshold),
        window: Number(v.rdConfig.window),
        minPrevious: Number(v.rdConfig.minPrevious),
      };
      if (v.rdConfig.fingerprintFilter) cfg.fingerprintFilter = v.rdConfig.fingerprintFilter;
      return cfg;
    }
    case 'STDDEV_ANOMALY': {
      const cfg: StdDevAnomalyConfig = {
        windowSize: Number(v.sdConfig.windowSize),
        deviations: Number(v.sdConfig.deviations),
        direction: v.sdConfig.direction,
        minDataPoints: Number(v.sdConfig.minDataPoints),
      };
      if (v.sdConfig.fingerprintFilter) cfg.fingerprintFilter = v.sdConfig.fingerprintFilter;
      return cfg;
    }
    case 'EDIVISIVE': {
      const cfg: EDivisiveConfig = {
        windowLen: Number(v.edConfig.windowLen),
        maxPvalue: Number(v.edConfig.maxPvalue),
        minMagnitude: Number(v.edConfig.minMagnitude),
        maxSeriesLength: Number(v.edConfig.maxSeriesLength),
      };
      if (v.edConfig.fingerprintFilter) cfg.fingerprintFilter = v.edConfig.fingerprintFilter;
      return cfg;
    }
    default:
      return undefined;
  }
}

type V = FormValidateOrFn<FormValues> | undefined;
type VA = FormAsyncValidateOrFn<FormValues> | undefined;
type NodeFormApi = ReactFormExtendedApi<FormValues, V, V, VA, V, VA, V, VA, V, VA, VA, unknown>;

interface DetectionConfigStepProps {
  form: NodeFormApi;
  nodeType: NodeType;
}

function renderField(form: NodeFormApi, f: DetectionFieldDef) {
  switch (f.kind) {
    case 'number':
      return (
        <form.Field key={f.id} name={f.name}>
          {(field) => (
            <NumberInput
              id={f.id}
              label={f.label}
              value={field.state.value}
              onChange={(_, { value }) => {
                field.handleChange(value);
              }}
              onBlur={field.handleBlur}
              helperText={f.helperText}
              allowEmpty={f.allowEmpty}
              hideSteppers
              invalid={field.state.meta.errors.length > 0}
              invalidText={fieldError(field.state.meta.errors)}
            />
          )}
        </form.Field>
      );
    case 'checkbox':
      return (
        <form.Field key={f.id} name={f.name}>
          {(field) => (
            <Checkbox
              id={f.id}
              labelText={f.label}
              checked={field.state.value}
              onChange={(_, { checked }) => {
                field.handleChange(checked);
              }}
            />
          )}
        </form.Field>
      );
    case 'select':
      return (
        <form.Field key={f.id} name={f.name}>
          {(field) => (
            <Select
              id={f.id}
              labelText={f.label}
              value={field.state.value}
              onChange={(e) => {
                field.handleChange(e.target.value as typeof field.state.value);
              }}
            >
              {f.options.map((o) => (
                <SelectItem key={o.value} value={o.value} text={o.text} />
              ))}
            </Select>
          )}
        </form.Field>
      );
  }
}

export const DetectionConfigStep = ({ form, nodeType }: DetectionConfigStepProps) => {
  const fields = DETECTION_FIELDS[nodeType];
  const schema = GROUP_SCHEMAS[nodeType];
  if (!fields || !schema) return null;

  const configName = fields[0]?.name.split('.')[0] as 'ftConfig' | 'rdConfig' | 'sdConfig' | 'edConfig';
  const validator = groupValidator(schema);
  return (
    <form.FormGroup name={configName} validators={{ onBlur: validator, onSubmit: validator }}>
      {() => <Stack gap={6}>{fields.map((f) => renderField(form, f))}</Stack>}
    </form.FormGroup>
  );
};
