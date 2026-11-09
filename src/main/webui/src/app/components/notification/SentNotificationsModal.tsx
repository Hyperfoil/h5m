import type { Notification } from '@client/types.gen';

import {
  ComposedModal,
  InlineNotification,
  ModalBody,
  ModalHeader,
  Pagination,
  SkeletonText,
  Stack,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Tag,
} from '@carbon/react';
import { sentNotificationsOptions } from '@client/@tanstack/react-query.gen';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

const STATUS_TAG_TYPE: Record<NonNullable<Notification['status']>, 'green' | 'red' | 'gray'> = {
  SENT: 'green',
  FAILED: 'red',
  SUPPRESSED: 'gray',
};

export const SentNotificationsModal = ({
  open,
  onClose,
  folderId,
  channelId,
}: {
  open: boolean;
  onClose: () => void;
  folderId: number;
  channelId?: number;
}) => {
  const {
    data: folderNotifications,
    isLoading,
    isError,
  } = useQuery({
    ...sentNotificationsOptions({ query: { folderId } }),
    enabled: open,
  });
  const notifications = channelId === undefined ? folderNotifications : folderNotifications?.filter((n) => n.channelId === channelId);

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const pageStart = (page - 1) * pageSize;
  const pagedNotifications = notifications?.slice(pageStart, pageStart + pageSize);

  return (
    <ComposedModal open={open} onClose={onClose} size="lg">
      <ModalHeader label="Notifications" title="Sent Notifications" closeModal={onClose} />
      <ModalBody>
        {isLoading && <SkeletonText paragraph lineCount={5} />}
        {isError && <InlineNotification kind="error" title="Failed to load notifications" lowContrast hideCloseButton />}
        {!isLoading && !isError && notifications?.length === 0 && (
          <InlineNotification kind="info" title="No notifications have been sent yet." lowContrast hideCloseButton />
        )}
        {!isLoading && !isError && notifications !== undefined && pagedNotifications !== undefined && notifications.length > 0 && (
          <Stack gap={3}>
            <StructuredListWrapper>
              <StructuredListHead>
                <StructuredListRow head>
                  <StructuredListCell head>Sent At</StructuredListCell>
                  <StructuredListCell head>Node</StructuredListCell>
                  <StructuredListCell head>Channel</StructuredListCell>
                  <StructuredListCell head>Changes</StructuredListCell>
                  <StructuredListCell head>Status</StructuredListCell>
                </StructuredListRow>
              </StructuredListHead>
              <StructuredListBody>
                {pagedNotifications.map((n) => (
                  <StructuredListRow key={n.id}>
                    <StructuredListCell>{n.sentAt?.toLocaleString()}</StructuredListCell>
                    <StructuredListCell>{n.nodeName}</StructuredListCell>
                    <StructuredListCell>{n.channelName ?? '(deleted)'}</StructuredListCell>
                    <StructuredListCell>{n.changeCount}</StructuredListCell>
                    <StructuredListCell>
                      <Tag type={n.status ? STATUS_TAG_TYPE[n.status] : 'gray'} size="sm">
                        {n.status}
                      </Tag>
                      {n.errorMessage && <div style={{ color: 'var(--cds-text-secondary)', fontSize: '0.75rem' }}>{n.errorMessage}</div>}
                    </StructuredListCell>
                  </StructuredListRow>
                ))}
              </StructuredListBody>
            </StructuredListWrapper>
            <Pagination
              totalItems={notifications.length}
              pageSize={pageSize}
              pageSizes={[10, 25, 50]}
              page={page}
              onChange={({ page: newPage, pageSize: newPageSize }) => {
                setPage(newPage);
                setPageSize(newPageSize);
              }}
              size="sm"
            />
          </Stack>
        )}
      </ModalBody>
    </ComposedModal>
  );
};
