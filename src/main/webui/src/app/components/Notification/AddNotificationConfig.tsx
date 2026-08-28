import {
  Button,
  MenuButton,
  MenuItem,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Tag,
} from '@carbon/react';
import { useState } from 'react';
import { channelsOptions } from '@client/@tanstack/react-query.gen';
import { useSuspenseQuery } from '@tanstack/react-query';
import type { NotificationChannel } from '@client/types.gen';
import CreateNotificationModal from '@app/components/Notification/CreateNotificationModal';
import DeleteNotiConfigModal from '@app/components/Notification/DeleteNotiConfigModal';
import EditNotiConfigModal from '@app/components/Notification/EditNotiConfigModal';

function getDestination(channel: NotificationChannel): string {
  const parsed = (channel.config ?? {}) as Record<string, unknown>;
  switch (channel.method) {
    case 'WEBHOOK':
      return String(parsed.url ?? '');
    case 'EMAIL':
      return Array.isArray(parsed.to) ? parsed.to.join(', ') : String(parsed.to ?? '');
    case 'SLACK':
      return String(parsed.channel ?? '');
    case 'GITHUB_ISSUE':
      return `${parsed.owner ?? ''}/${parsed.repo ?? ''}`;
    default:
      return '';
  }
}

export default function AddNotificationConfig({ folderId }: { folderId: number }) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [channelToDelete, setChannelToDelete] = useState<NotificationChannel | null>(null);
  const [channelToEdit, setChannelToEdit] = useState<NotificationChannel | null>(null);
  const { data: channels } = useSuspenseQuery(channelsOptions({ query: { folderId } }));

  return (
    <>
      <Button kind="primary" size="md" onClick={() => setIsCreateOpen(true)} className="create-notification-btn" style={{ margin: 'var(--cds-spacing-05)' }}>
        Create Notification Channel
      </Button>
      <CreateNotificationModal open={isCreateOpen} onClose={() => setIsCreateOpen(false)} folderId={folderId} />
      <DeleteNotiConfigModal open={channelToDelete !== null} onClose={() => setChannelToDelete(null)} channel={channelToDelete} />
      <EditNotiConfigModal open={channelToEdit !== null} onClose={() => setChannelToEdit(null)} channel={channelToEdit} />
      {channels.length === 0 ? (
        <p style={{ margin: 'var(--cds-spacing-05)' }}>No notification channels configured</p>
      ) : (
        <StructuredListWrapper>
          <StructuredListHead>
            <StructuredListRow head>
              <StructuredListCell head>Method</StructuredListCell>
              <StructuredListCell head>Enabled</StructuredListCell>
              <StructuredListCell head>Destination</StructuredListCell>
              <StructuredListCell head />
            </StructuredListRow>
          </StructuredListHead>
          <StructuredListBody>
            {channels?.map((channel: NotificationChannel) => (
              <StructuredListRow key={channel.id}>
                <StructuredListCell>
                  <Tag size="sm">{channel.method}</Tag>
                </StructuredListCell>
                <StructuredListCell>{channel.enabled ? 'Yes' : 'No'}</StructuredListCell>
                <StructuredListCell>{getDestination(channel)}</StructuredListCell>
                <StructuredListCell>
                  <MenuButton label="Action" kind="ghost" size="sm" menuAlignment="bottom-end">
                    <MenuItem label="Delete" kind="danger" onClick={() => setChannelToDelete(channel)} />
                    <MenuItem label="Edit" onClick={() => setChannelToEdit(channel)} />
                  </MenuButton>
                </StructuredListCell>
              </StructuredListRow>
            ))}
          </StructuredListBody>
        </StructuredListWrapper>
      )}
    </>
  );
}
