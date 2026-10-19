import { UploadDataModal } from '@app/components/Upload/UploadDataModal';
import {
  Button,
  Pagination,
  Stack,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Tag,
} from '@carbon/react';
import { useEffect, useState } from 'react';
import { useFolderPermission } from '@app/context/usePermission.tsx';
export const UploadTab = ({ folderId }: { folderId: number }) => {
  const canModify = useFolderPermission(folderId);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [recentUploads, setRecentUploads] = useState<{ fileName: string; uploadId: number; uploadedAt: Date }[]>([]);
  const [uploadsPage, setUploadsPage] = useState(1);
  const [uploadsPageSize, setUploadsPageSize] = useState(10);

  useEffect(() => {
    setRecentUploads([]);
    setUploadsPage(1);
  }, [folderId]);

  const uploadsStart = (uploadsPage - 1) * uploadsPageSize;
  const pagedUploads = recentUploads.slice(uploadsStart, uploadsStart + uploadsPageSize);

  return (
    <Stack gap={5}>
      <Button
        kind="primary"
        size="md"
        disabled={!canModify}
        onClick={() => {
          setUploadModalOpen(true);
        }}
      >
        Upload data
      </Button>
      {recentUploads.length > 0 ? (
        <Stack gap={3}>
          <StructuredListWrapper>
            <StructuredListHead>
              <StructuredListRow head>
                <StructuredListCell head>Upload file</StructuredListCell>
                <StructuredListCell head>Upload ID</StructuredListCell>
                <StructuredListCell head>Uploaded at</StructuredListCell>
              </StructuredListRow>
            </StructuredListHead>
            <StructuredListBody>
              {pagedUploads.map((item, i) => (
                <StructuredListRow key={`${String(item.uploadId)}-${String(i)}`}>
                  <StructuredListCell>{item.fileName}</StructuredListCell>
                  <StructuredListCell>
                    <Tag type="green" size="sm">
                      #{item.uploadId}
                    </Tag>
                  </StructuredListCell>
                  <StructuredListCell>{item.uploadedAt.toLocaleString()}</StructuredListCell>
                </StructuredListRow>
              ))}
            </StructuredListBody>
          </StructuredListWrapper>
          <Pagination
            totalItems={recentUploads.length}
            pageSize={uploadsPageSize}
            pageSizes={[10, 25, 50]}
            page={uploadsPage}
            onChange={({ page, pageSize }) => {
              setUploadsPage(page);
              setUploadsPageSize(pageSize);
            }}
            size="sm"
          />
        </Stack>
      ) : (
        <p>No uploads to show.</p>
      )}
      <UploadDataModal
        open={uploadModalOpen}
        onClose={() => {
          setUploadModalOpen(false);
        }}
        folderId={folderId}
        onUploadSuccess={(fileName, uploadId) => {
          setRecentUploads((prev) => [{ fileName, uploadId, uploadedAt: new Date() }, ...prev]);
          setUploadsPage(1);
        }}
      />
    </Stack>
  );
};
