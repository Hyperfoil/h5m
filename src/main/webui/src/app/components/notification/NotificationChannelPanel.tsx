import type { NotificationChannel } from '@client/types.gen';

import { NotificationChannelModal } from '@app/components/notification/NotificationChannelModal';
import { useNotification } from '@app/context/useNotification.tsx';
import { Add, Edit, TrashCan } from '@carbon/icons-react';
import { ContainedList, ContainedListItem, ExpandableSearch, IconButton, InlineNotification, Modal } from '@carbon/react';
import { channelsOptions, deleteChannelMutation } from '@client/@tanstack/react-query.gen';
import { useMutation, useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { useState } from 'react';

function getDestination({ config }: NotificationChannel): string {
  switch (config.method) {
    case 'WEBHOOK':
      return config.url;
    case 'EMAIL':
      return config.to.join(', ');
    case 'SLACK':
      return config.channel;
    case 'GITHUB_ISSUE':
      return `${config.owner}/${config.repo}`;
  }
}

export const NotificationChannelPanel = ({ folderId }: { folderId: number }) => {
  const queryClient = useQueryClient();
  const notifications = useNotification();
  const { data: channels } = useSuspenseQuery(channelsOptions({ query: { folderId } }));

  const [createOpen, setCreateOpen] = useState(false);
  const [editChannel, setEditChannel] = useState<NotificationChannel | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<NotificationChannel | null>(null);
  const [channelFilter, setChannelFilter] = useState('');

  const deleteChannel = useMutation({
    ...deleteChannelMutation(),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: channelsOptions({ query: { folderId } }).queryKey });
      const deleted = channels.find((c) => c.id === variables.path.id);
      notifications.success(`Notification channel '${deleted?.name ?? ''}' deleted`);
      setConfirmDelete(null);
    },
    onError: (e, variables) => {
      const deleted = channels.find((c) => c.id === variables.path.id);
      notifications.handleError(`Failed to delete Notification channel '${deleted?.name ?? ''}'`, e);
      setConfirmDelete(null);
    },
  });

  const filteredChannels = channels.filter(
    (c) =>
      !channelFilter ||
      (c.name ?? '').toLowerCase().includes(channelFilter.toLowerCase()) ||
      getDestination(c).toLowerCase().includes(channelFilter.toLowerCase()),
  );

  return (
    <>
      <ContainedList
        label="Notification Channels"
        kind="on-page"
        action={
          <>
            <ExpandableSearch
              id="channel-filter"
              size="lg"
              labelText="Filter Notification Channel"
              placeholder="Filter Notification Channel"
              value={channelFilter}
              onChange={(e) => {
                setChannelFilter(e.target.value);
              }}
              onClear={() => {
                setChannelFilter('');
              }}
            />
            <IconButton
              label="Create Notification Channel"
              kind="ghost"
              onClick={() => {
                setCreateOpen(true);
              }}
            >
              <Add />
            </IconButton>
          </>
        }
      >
        {filteredChannels.map((channel) => (
          <ContainedListItem
            key={channel.id}
            action={
              <>
                <IconButton
                  label="Edit Notification Channel"
                  kind="ghost"
                  onClick={(e) => {
                    e.stopPropagation();
                    setEditChannel(channel);
                  }}
                >
                  <Edit />
                </IconButton>
                <IconButton
                  label="Delete Notification Channel"
                  kind={'danger--ghost' as 'ghost'}
                  onClick={(e) => {
                    e.stopPropagation();
                    setConfirmDelete(channel);
                  }}
                >
                  <TrashCan />
                </IconButton>
              </>
            }
          >
            <span style={channel.enabled ? { color: 'var(--cds-support-success)' } : { color: 'var(--cds-text-disabled)' }}>
              {channel.name ?? getDestination(channel)}
              {!channel.enabled && ' (disabled)'}
            </span>
          </ContainedListItem>
        ))}
        {channels.length === 0 && <InlineNotification kind="info" title="Create a notification channel to get started." lowContrast hideCloseButton />}
        {channels.length !== 0 && filteredChannels.length === 0 && (
          <InlineNotification kind="info" title="No notification channels found." lowContrast hideCloseButton />
        )}
      </ContainedList>

      <NotificationChannelModal
        open={createOpen}
        onClose={() => {
          setCreateOpen(false);
        }}
        folderId={folderId}
      />

      <NotificationChannelModal
        key={editChannel?.id}
        open={editChannel !== null}
        onClose={() => {
          setEditChannel(null);
        }}
        folderId={folderId}
        channel={editChannel}
      />

      <Modal
        open={confirmDelete !== null}
        danger
        modalLabel={'Delete Notification Channel'}
        modalHeading={confirmDelete?.name ?? (confirmDelete ? getDestination(confirmDelete) : '')}
        primaryButtonText="Delete"
        secondaryButtonText="Cancel"
        onRequestClose={() => {
          setConfirmDelete(null);
        }}
        onSecondarySubmit={() => {
          setConfirmDelete(null);
        }}
        onRequestSubmit={() => {
          deleteChannel.mutate({ path: { id: confirmDelete?.id ?? 0 } });
        }}
      >
        <p>Are you sure you want to delete this notification channel? This action cannot be undone.</p>
      </Modal>
    </>
  );
};
