import type { NotificationChannel, NotificationMethod } from '@client/types.gen';
import type { ZodType } from 'zod';

import { ListTextInput } from '@app/components/form/ListTextInput.tsx';
import { extractErrorMessage } from '@app/context/NotificationProvider.tsx';
import { useNotification } from '@app/context/useNotification.tsx';
import { fieldError, groupValidator } from '@app/validation';
import {
  Button,
  ComposedModal,
  Form,
  InlineNotification,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ProgressIndicator,
  ProgressStep,
  Select,
  SelectItem,
  Stack,
  TextArea,
  TextInput,
  Toggle,
} from '@carbon/react';
import { channelsOptions, createChannelMutation, updateChannelMutation } from '@client/@tanstack/react-query.gen';
import { zEmailConfig, zGitHubIssueConfig, zNotificationChannel, zNotificationMethod, zSlackConfig, zTokenSecret, zWebhookConfig } from '@client/zod.gen.ts';
import { useForm, useSelector } from '@tanstack/react-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';

interface NotificationChannelModalProps {
  open: boolean;
  onClose: () => void;
  folderId: number;
  channel?: NotificationChannel | null;
}

type StepKey = 'method' | 'config' | 'template';

const STEP_LABEL: Record<StepKey, string> = {
  method: 'Method',
  config: 'Configuration',
  template: 'Template',
};

const STEPS: StepKey[] = ['method', 'config', 'template'];

interface FormValues {
  name: string;
  method: NotificationMethod;
  template: string;
  enabled: boolean;
  webhook: { url: string; authHeader: string };
  email: { to: string[]; subject: string };
  slack: { channel: string; token: string };
  github: { owner: string; repo: string; title: string; labels: string[]; token: string };
}

const DEFAULT_VALUES: FormValues = {
  name: '',
  method: 'WEBHOOK',
  template: '',
  enabled: true,
  webhook: { url: '', authHeader: '' },
  email: { to: [], subject: '' },
  slack: { channel: '', token: '' },
  github: { owner: '', repo: '', title: '', labels: [], token: '' },
};

interface FieldDef {
  name:
    | 'webhook.url'
    | 'webhook.authHeader'
    | 'email.to'
    | 'email.subject'
    | 'slack.channel'
    | 'slack.token'
    | 'github.owner'
    | 'github.repo'
    | 'github.title'
    | 'github.labels'
    | 'github.token';
  id: string;
  label: string;
  secret?: boolean; // a secret is write-only, so it is set on creation and disabled afterwards
  list?: boolean; // a list property is edited as comma-separated text
  type?: string;
  placeholder?: string;
  helperText?: string;
}

// secrets are never returned by the server, so editing validates the configuration alone
const SECRET_EDIT_HELPER_TEXT = 'Write-only: the stored value is kept and cannot be changed here';

const METHODS: Record<NotificationMethod, { label: string; schema: ZodType; editSchema?: ZodType; fields: FieldDef[] }> = {
  WEBHOOK: {
    label: 'Web Hook',
    schema: zWebhookConfig,
    fields: [
      { name: 'webhook.url', id: 'webhook-url', label: 'URL (required)', type: 'url', placeholder: 'https://example.com/hook' },
      {
        name: 'webhook.authHeader',
        id: 'webhook-auth-header',
        label: 'Authorization header (optional)',
        secret: true,
        type: 'password',
        helperText: 'Sent as the Authorization header, e.g. Bearer ***',
      },
    ],
  },
  EMAIL: {
    label: 'Email',
    schema: zEmailConfig,
    fields: [
      {
        name: 'email.to',
        list: true,
        id: 'email-to',
        label: 'Recipients (required)',
        placeholder: 'alice@example.com, bob@example.com',
        helperText: 'Comma-separated list of addresses',
      },
      { name: 'email.subject', id: 'email-subject', label: 'Subject (optional)', placeholder: 'e.g. Regression detected' },
    ],
  },
  SLACK: {
    label: 'Slack',
    schema: zSlackConfig.extend(zTokenSecret.omit({ method: true }).shape),
    editSchema: zSlackConfig,
    fields: [
      { name: 'slack.channel', id: 'slack-channel', label: 'Channel (required)', placeholder: '#alerts' },
      {
        name: 'slack.token',
        id: 'slack-token',
        label: 'Token (required)',
        secret: true,
        type: 'password',
      },
    ],
  },
  GITHUB_ISSUE: {
    label: 'GitHub Issue',
    schema: zGitHubIssueConfig.extend(zTokenSecret.omit({ method: true }).shape),
    editSchema: zGitHubIssueConfig,
    fields: [
      { name: 'github.owner', id: 'github-owner', label: 'Owner (required)', placeholder: 'e.g. Hyperfoil' },
      { name: 'github.repo', id: 'github-repo', label: 'Repository (required)', placeholder: 'e.g. horreum' },
      { name: 'github.title', id: 'github-title', label: 'Title (optional)', placeholder: 'e.g. Regression detected' },
      {
        name: 'github.labels',
        list: true,
        id: 'github-labels',
        label: 'Labels (optional)',
        placeholder: 'e.g. bug, regression',
        helperText: 'Comma-separated list of labels',
      },
      {
        name: 'github.token',
        id: 'github-token',
        label: 'Token (required)',
        secret: true,
        type: 'password',
      },
    ],
  },
};

const toFormValues = (channel: NotificationChannel | null | undefined): FormValues => {
  if (!channel) {
    return DEFAULT_VALUES;
  }
  const values = {
    ...DEFAULT_VALUES,
    name: channel.name ?? '',
    template: channel.template ?? '',
    enabled: channel.enabled ?? true,
    method: channel.config.method,
  };
  switch (channel.config.method) {
    case 'WEBHOOK':
      return { ...values, webhook: { url: channel.config.url, authHeader: '' } };
    case 'EMAIL':
      return { ...values, email: { to: channel.config.to, subject: channel.config.subject ?? '' } };
    case 'SLACK':
      return { ...values, slack: { channel: channel.config.channel, token: '' } };
    case 'GITHUB_ISSUE':
      return {
        ...values,
        github: {
          owner: channel.config.owner,
          repo: channel.config.repo,
          title: channel.config.title ?? '',
          labels: channel.config.labels ?? [],
          token: '',
        },
      };
  }
};

// an empty secret is omitted: on create there is none, on update it leaves the stored one unchanged
// a blank name is omitted on create so the server generates one, but sent on edit so clearing it is rejected rather than ignored
// a blank template is always sent, so clearing it on edit is applied instead of leaving the stored one in place
const buildChannel = (v: FormValues, isEdit: boolean): NotificationChannel => {
  const channel = {
    name: isEdit ? v.name.trim() : v.name.trim() || undefined,
    template: v.template.trim(),
    enabled: v.enabled,
  };
  switch (v.method) {
    case 'WEBHOOK':
      return {
        ...channel,
        config: { method: 'WEBHOOK', url: v.webhook.url },
        secret: v.webhook.authHeader ? { method: 'WEBHOOK', authHeader: v.webhook.authHeader } : undefined,
      };
    case 'EMAIL':
      return { ...channel, config: { method: 'EMAIL', to: v.email.to, subject: v.email.subject || undefined } };
    case 'SLACK':
      return {
        ...channel,
        config: { method: 'SLACK', channel: v.slack.channel },
        secret: v.slack.token ? { method: 'SLACK', token: v.slack.token } : undefined,
      };
    case 'GITHUB_ISSUE':
      return {
        ...channel,
        config: {
          method: 'GITHUB_ISSUE',
          owner: v.github.owner,
          repo: v.github.repo,
          title: v.github.title || undefined,
          labels: v.github.labels.length > 0 ? v.github.labels : undefined,
        },
        secret: v.github.token ? { method: 'GITHUB_ISSUE', token: v.github.token } : undefined,
      };
  }
};

export const NotificationChannelModal = ({ open, onClose, folderId, channel }: NotificationChannelModalProps) => {
  const isEdit = channel != null;
  const [currentStep, setCurrentStep] = useState(0);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const notifications = useNotification();
  const queryClient = useQueryClient();

  const onSuccess = () => {
    void queryClient.invalidateQueries({ queryKey: channelsOptions({ query: { folderId } }).queryKey });
    notifications.success(isEdit ? 'Notification channel updated' : 'Notification channel created');
    handleClose();
  };

  const createChannel = useMutation({
    ...createChannelMutation(),
    onSuccess,
    onError: (e) => {
      setSubmitError(extractErrorMessage(e) ?? 'Failed to create notification channel');
    },
  });

  const updateChannel = useMutation({
    ...updateChannelMutation(),
    onSuccess,
    onError: (e) => {
      setSubmitError(extractErrorMessage(e) ?? 'Failed to update notification channel');
    },
  });

  const mutation = isEdit ? updateChannel : createChannel;

  const form = useForm({
    defaultValues: toFormValues(channel),
    onSubmit: ({ value }) => {
      setSubmitError(null);
      if (isEdit) {
        updateChannel.mutate({ path: { id: channel.id ?? 0 }, body: buildChannel(value, true) });
      } else {
        createChannel.mutate({ query: { folderId }, body: buildChannel(value, false) });
      }
    },
  });

  // the configuration group, captured so that saving from another step can validate it
  const configGroup = useRef<{ handleSubmit: () => Promise<void> } | null>(null);

  const method = useSelector(form.store, (s) => s.values.method);
  const validator = groupValidator((isEdit ? METHODS[method].editSchema : undefined) ?? METHODS[method].schema);
  const group = METHODS[method].fields[0]?.name.split('.')[0] as 'webhook' | 'email' | 'slack' | 'github';

  /**
   * The configuration fields are on their own step, so saving from the template step has to validate them
   * explicitly: the form submit skips group validators. An invalid configuration sends the user back to the
   * step showing it, with the errors of the group validator on the offending fields.
   */
  const handleSave = () => {
    void configGroup.current?.handleSubmit().then(() => {
      if (form.state.isFieldsValid) {
        void form.handleSubmit();
      } else {
        setCurrentStep(STEPS.indexOf('config'));
      }
    });
  };

  const handleNext = () => {
    void form.validateAllFields('submit').then(() => {
      if (form.state.isFieldsValid) {
        setCurrentStep((s) => s + 1);
      }
    });
  };

  const handleClose = () => {
    form.reset();
    setCurrentStep(0);
    setSubmitError(null);
    onClose();
  };

  const resetDependentFields = (newMethod: NotificationMethod) => {
    const name = form.getFieldValue('name');
    form.reset();
    form.setFieldValue('name', name);
    form.setFieldValue('method', newMethod);
  };

  const renderTextField = (f: FieldDef) => (
    <form.Field key={f.id} name={f.name}>
      {(field) => (
        <TextInput
          id={f.id}
          type={f.type}
          labelText={f.label}
          disabled={isEdit && f.secret}
          placeholder={f.placeholder}
          helperText={isEdit && f.secret ? SECRET_EDIT_HELPER_TEXT : f.helperText}
          value={field.state.value as string}
          onChange={(e) => {
            field.handleChange(e.target.value);
          }}
          onBlur={field.handleBlur}
          invalid={field.state.meta.errors.length > 0}
          invalidText={fieldError(field.state.meta.errors)}
        />
      )}
    </form.Field>
  );

  const renderListField = (f: FieldDef) => (
    <form.Field key={f.id} name={f.name}>
      {(field) => (
        <ListTextInput
          id={f.id}
          labelText={f.label}
          placeholder={f.placeholder}
          helperText={f.helperText}
          value={field.state.value as string[]}
          onChange={(value) => {
            field.handleChange(value);
          }}
          onBlur={field.handleBlur}
          invalid={field.state.meta.errors.length > 0}
          invalidText={fieldError(field.state.meta.errors)}
        />
      )}
    </form.Field>
  );

  return (
    <ComposedModal open={open} onClose={handleClose} size="lg">
      <ModalHeader
        label={isEdit ? 'Edit Notification Channel' : undefined}
        title={isEdit ? (channel.name ?? METHODS[method].label) : 'Create Notification Channel'}
        closeModal={handleClose}
      />
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
              {STEPS.map((s) => (
                <ProgressStep key={s} label={STEP_LABEL[s]} />
              ))}
            </ProgressIndicator>

            {STEPS[currentStep] === 'method' && (
              <Stack gap={6}>
                <form.Field name="name" validators={{ onBlur: zNotificationChannel.shape.name.unwrap(), onSubmit: zNotificationChannel.shape.name.unwrap() }}>
                  {(field) => (
                    <TextInput
                      id="channel-name"
                      labelText="Name (optional)"
                      placeholder="e.g. team-alerts"
                      helperText="Generated from the method when left empty"
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

                <form.Field name="method">
                  {(field) => (
                    <Select
                      id="channel-method"
                      labelText="Method"
                      // the method is intrinsic to an existing channel: switching it would invalidate the whole configuration
                      disabled={isEdit}
                      value={field.state.value}
                      onChange={(e) => {
                        const newMethod = e.target.value as NotificationMethod;
                        field.handleChange(newMethod);
                        resetDependentFields(newMethod);
                      }}
                    >
                      {zNotificationMethod.options.map((m) => (
                        <SelectItem key={m} value={m} text={METHODS[m].label} />
                      ))}
                    </Select>
                  )}
                </form.Field>

                {isEdit && (
                  <form.Field name="enabled">
                    {(field) => (
                      <Toggle
                        id="channel-enabled"
                        labelText="Enabled"
                        labelA="Off"
                        labelB="On"
                        toggled={field.state.value}
                        onToggle={(checked) => {
                          field.handleChange(checked);
                        }}
                      />
                    )}
                  </form.Field>
                )}
              </Stack>
            )}

            {/* kept mounted on every step: the group validator is what validates the configuration on save */}
            <div hidden={STEPS[currentStep] !== 'config'}>
              <form.FormGroup name={group} validators={{ onBlur: validator, onSubmit: validator }}>
                {(groupApi) => {
                  configGroup.current = groupApi;
                  return <Stack gap={6}>{METHODS[method].fields.map((f) => (f.list ? renderListField(f) : renderTextField(f)))}</Stack>;
                }}
              </form.FormGroup>
            </div>

            {STEPS[currentStep] === 'template' && (
              <Stack gap={6}>
                <form.Field name="template">
                  {(field) => (
                    <TextArea
                      id="channel-template"
                      labelText="Message template (optional)"
                      rows={4}
                      helperText="Leave empty to use the default message"
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
              </Stack>
            )}

            {submitError && (
              <InlineNotification
                kind="error"
                lowContrast
                title={isEdit ? 'Failed to update notification channel' : 'Failed to create notification channel'}
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
        {currentStep === STEPS.length - 1 ? (
          <Button
            kind="primary"
            disabled={mutation.isPending}
            onClick={handleSave}
          >
            {mutation.isPending ? 'Saving...' : 'Save'}
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
