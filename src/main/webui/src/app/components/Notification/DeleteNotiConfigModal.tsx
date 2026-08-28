import { Modal } from '@carbon/react';
import { deleteChannelMutation } from '@client/@tanstack/react-query.gen.ts';
import { extractErrorMessage } from '@app/context/NotificationProvider.tsx';
import { useNotification } from '@app/context/useNotification.tsx';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { NotificationChannel } from '@client/types.gen';
import { useState } from 'react';

interface DeleteNotiConfigModalProps {
  open: boolean;
  onClose: () => void;
  channel: NotificationChannel | null;
}

export default function DeleteNotiConfigModal({ open, onClose, channel }: DeleteNotiConfigModalProps){
  const queryClient = useQueryClient();
  const notifications = useNotification();
  const [error, setError] = useState<string | null>(null);

   const deleteChannel = useMutation({
      ...deleteChannelMutation(),
      onSuccess: () => {
        void queryClient.invalidateQueries();
        notifications.success('Notification channel deleted');
        handleClose();
      },
      onError: (e) => {
        notifications.error(extractErrorMessage(e) ?? 'Failed to delete notification channel');
      },
    });
  const handleDelete = ()=>{
    if (!channel?.id) return;
    deleteChannel.mutate({ path: { id: channel.id } })
    }

  const handleClose = ()=>{
    setError('');
    onClose();
    };

  return (
    <Modal
      open={open}
      danger
      modalHeading="Delete channel"
      modalLabel={channel?.name ?? ''}
      primaryButtonText={deleteChannel.isPending ? 'Deleting…' : 'Delete'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={deleteChannel.isPending}
      onRequestSubmit={handleDelete}
      onRequestClose={() => { setError(null); handleClose(); }}
    >
      <p>
        Are you sure you want to delete <strong>{channel?.name}</strong>?
      </p>
      <p style={{ marginTop: '0.75rem', color: 'var(--cds-text-secondary)' }}>
         This action cannot be undone.
      </p>
      {error && (
        <p style={{ marginTop: '0.75rem', color: 'var(--cds-support-error)' }}>
          {error}
        </p>
      )}
    </Modal>
  );
}
