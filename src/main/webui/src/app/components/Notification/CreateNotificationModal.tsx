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
  Select,
  SelectItem,
  Stack,
  TextInput,
} from '@carbon/react';
import { createChannelMutation } from '@client/@tanstack/react-query.gen';
import { zNotificationMethod } from '@client/zod.gen.ts';
import { useForm, useSelector } from '@tanstack/react-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { NotificationConfiguration, NotificationMethod, NotificationSecret } from '@client/types.gen';
import { useState } from 'react';
import { z } from 'zod';

interface CreateNotificationModalProps {
  open: boolean;
  onClose: () => void;
  folderId: number;
}

interface FormValues {
  method: string;
  url: string;
  auth: string;
  recipients: string;
  subject: string;
  channel: string;
  slackToken: string;
  repo: string;
  owner: string;
  title: string;
  label: string;
  githubToken: string;
}

const DEFAULT_VALUES: FormValues = {
  method: '',
  url: '',
  auth: '',
  recipients: '',
  subject: '',
  channel: '',
  slackToken: '',
  repo: '',
  owner: '',
  title: '',
  label: '',
  githubToken: '',
};

const required = z.string().min(1, 'Required');

export default function CreateNotificationModal({ open, onClose, folderId }: CreateNotificationModalProps) {
  const notifications = useNotification();
  const queryClient = useQueryClient();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const createChannel = useMutation({
    ...createChannelMutation(),
    onSuccess: () => {
      void queryClient.invalidateQueries();
      notifications.success('Notification channel created');
      handleClose();
    },
    onError: (e) => {
      setSubmitError(extractErrorMessage(e) ?? 'Failed to create notification channel');
    },
  });

  const form = useForm({
    defaultValues: DEFAULT_VALUES,
    onSubmit: ({ value }) => {
      setSubmitError(null);
      let config: NotificationConfiguration;
      let secret: NotificationSecret | undefined;

      switch (value.method) {
        case 'WEBHOOK':
          config = { method: 'WEBHOOK', url: value.url };
          secret = value.auth ? { method: 'WEBHOOK', authHeader: value.auth } : undefined;
          break;
        case 'EMAIL':
          config = { method: 'EMAIL', to: value.recipients.split(',').map((r) => r.trim()).filter(Boolean), subject: value.subject };
          break;
        case 'SLACK':
          config = { method: 'SLACK', channel: value.channel };
          secret = { method: 'SLACK', token: value.slackToken };
          break;
        case 'GITHUB_ISSUE':
          config = {
            method: 'GITHUB_ISSUE',
            repo: value.repo,
            owner: value.owner,
            title: value.title || undefined,
            labels: value.label ? [value.label] : undefined,
          };
          secret = { method: 'GITHUB_ISSUE', token: value.githubToken };
          break;
        default:
          return;
      }

      createChannel.mutate({
        query: { folderId },
        body: {
          method: value.method as NotificationMethod,
          config,
          secret,
        },
      });
    },
  });

  const method = useSelector(form.store, (s) => s.values.method);

  const handleClose = () => {
    form.reset();
    setSubmitError(null);
    onClose();
  };

  return (
    <ComposedModal open={open} onClose={handleClose}>
      <ModalHeader title="Create Notification" closeModal={handleClose} />
      <ModalBody>
        <Form onSubmit={(e) => e.preventDefault()}>
          <Stack gap={6}>
            <p style={{ marginBottom: '1rem' }}>Configure your new notification here.</p>

            <form.Field name="method" validators={{ onSubmit: zNotificationMethod }}>
              {(field) => (
                <Select
                  id="config"
                  labelText="Method"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  invalid={field.state.meta.errors.length > 0}
                  invalidText={fieldError(field.state.meta.errors)}
                >
                  <SelectItem value="" text="Choose an option" disabled hidden />
                  <SelectItem value="WEBHOOK" text="Web Hook" />
                  <SelectItem value="EMAIL" text="Email" />
                  <SelectItem value="SLACK" text="Slack" />
                  <SelectItem value="GITHUB_ISSUE" text="Github Issue" />
                </Select>
              )}
            </form.Field>

            {method === 'WEBHOOK' && (
              <>
                <form.Field name="url" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="url-name"
                      type="url"
                      labelText="Url (required)"
                      placeholder="e.g. 'Http / Https '"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
                <form.Field name="auth">
                  {(field) => (
                    <TextInput
                      id="Auth-name"
                      labelText="Auth (optional)"
                      placeholder="e.g. jascjsa"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                    />
                  )}
                </form.Field>
              </>
            )}

            {method === 'EMAIL' && (
              <>
                <form.Field name="recipients" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="Recipients-name"
                      type="email"
                      labelText="Recipients (required)"
                      placeholder="e.g. abcd@example.com"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
                <form.Field name="subject">
                  {(field) => (
                    <TextInput
                      id="subject-name"
                      labelText="Subject (optional)"
                      placeholder="e.g. Regression detected for abcd node"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                    />
                  )}
                </form.Field>
              </>
            )}

            {method === 'SLACK' && (
              <>
                <form.Field name="channel" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="Channel-name"
                      labelText="Channel (required)"
                      placeholder="e.g. #alerts"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
                <form.Field name="slackToken" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="token-name"
                      labelText="Secret (required)"
                      placeholder="e.g. ***"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
              </>
            )}

            {method === 'GITHUB_ISSUE' && (
              <>
                <form.Field name="repo" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="Repo-name"
                      labelText="Repo (required)"
                      placeholder="e.g. test github repo"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
                <form.Field name="owner" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="owner-name"
                      labelText="Owner (required)"
                      placeholder="e.g. Owner name"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
                <form.Field name="title">
                  {(field) => (
                    <TextInput
                      id="title-name"
                      labelText="Title (optional)"
                      placeholder="e.g. Regression detected"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                    />
                  )}
                </form.Field>
                <form.Field name="label">
                  {(field) => (
                    <TextInput
                      id="Label-name"
                      labelText="Label (optional)"
                      placeholder="e.g. bug"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                    />
                  )}
                </form.Field>
                <form.Field name="githubToken" validators={{ onSubmit: required, onBlur: required }}>
                  {(field) => (
                    <TextInput
                      id="github-token-name"
                      labelText="Token (required)"
                      placeholder="e.g. ***"
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      invalid={field.state.meta.errors.length > 0}
                      invalidText={fieldError(field.state.meta.errors)}
                    />
                  )}
                </form.Field>
              </>
            )}

            {submitError && (
              <InlineNotification
                kind="error"
                lowContrast
                title="Failed to create notification"
                subtitle={submitError}
                onCloseButtonClick={() => setSubmitError(null)}
              />
            )}
          </Stack>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={handleClose}>
          Cancel
        </Button>
        <Button kind="primary" disabled={createChannel.isPending} onClick={() => void form.handleSubmit()}>
          {createChannel.isPending ? 'Saving...' : 'Save'}
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
}
