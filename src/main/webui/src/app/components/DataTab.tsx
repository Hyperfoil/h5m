import type { View, ViewComponent } from '@client/types.gen.ts';

import { UploadDataModal } from '@app/components/UploadDataModal';
import { ViewConfigModal } from '@app/components/ViewConfigModal';
import {
  Button,
  DataTable,
  Dropdown,
  ErrorBoundary,
  InlineLoading,
  Pagination,
  SkeletonText,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { getViewDataOptions, getViewsOptions } from '@client/@tanstack/react-query.gen.ts';
import { useQuery } from '@tanstack/react-query';
import { Suspense, useMemo, useState } from 'react';

// Name of the system-managed default view. Mirrors ReservedNamespace.DEFAULT_VIEW_NAME on the backend.
// Keep in sync (no constant is emitted into the generated OpenAPI client, so this cannot be imported).
const DEFAULT_VIEW_NAME = 'h5m.default';

function formatCellValue(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

const ViewDataTable = ({
  folderId,
  view,
}: {
  folderId: number;
  view: View;
}) => {
  const viewId = view.id;
  const { data: rows, isLoading, isError } = useQuery(
    getViewDataOptions({
      path: { folderId, viewId: viewId! },
    }),
  );

  if (isLoading) return <SkeletonText paragraph={true} lineCount={5} />;
  if (isError) return <InlineLoading status="error" description="Failed to load view data" />;
  if (!rows || rows.length === 0) return <p>No data available</p>;

  const columns = (view.components ?? [])
    .sort((a: ViewComponent, b: ViewComponent) => (a.headerOrder ?? 0) - (b.headerOrder ?? 0))
    .map((c: ViewComponent) => ({
      key: c.nodeName ?? c.headerName ?? '',
      header: c.headerName ?? c.nodeName ?? '',
    }));

  const tableRows = rows.map((row: Record<string, unknown>, idx: number) => ({
    id: String(idx),
    ...Object.fromEntries(columns.map((col) => [col.key, formatCellValue(row[col.key])])),
  }));

  return (
    <DataTable rows={tableRows} headers={columns}>
      {({ rows: dataRows, headers, getTableProps, getHeaderProps, getRowProps }) => (
        <Table {...getTableProps()}>
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader {...getHeaderProps({ header })}>
                  {header.header}
                </TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {dataRows.map((row) => (
              <TableRow {...getRowProps({ row })}>
                {row.cells.map((cell) => (
                  <TableCell key={cell.id}>{cell.value}</TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </DataTable>
  );
};

export const DataTab = ({ folderId, groupId }: { folderId: number; groupId: number }) => {
  const { data: views, isLoading: viewsLoading } = useQuery(
    getViewsOptions({ path: { folderId } }),
  );
  const [selectedViewId, setSelectedViewId] = useState<number | null>(null);
  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [editingView, setEditingView] = useState<View | null>(null);
  const [modalKey, setModalKey] = useState(0);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [recentUploads, setRecentUploads] = useState<Array<{ fileName: string; uploadId: number; uploadedAt: Date }>>([]);
  const [uploadsPage, setUploadsPage] = useState(1);
  const [uploadsPageSize, setUploadsPageSize] = useState(10);

  const selectedView = useMemo((): View | null => {
    if (!views || views.length === 0) return null;
    if (selectedViewId != null) {
      return views.find((v: View) => v.id === selectedViewId) ?? views[0] ?? null;
    }
    // Prefer the system default view if it has components, otherwise pick the first view with components.
    const defaultView = views.find((v: View) => v.name === DEFAULT_VIEW_NAME) ?? null;
    if (defaultView && defaultView.components && defaultView.components.length > 0) {
      return defaultView;
    }
    const viewWithComponents = views.find((v: View) => v.components && v.components.length > 0) ?? null;
    return viewWithComponents ?? defaultView ?? views[0] ?? null;
  }, [views, selectedViewId]);

  if (viewsLoading) return <SkeletonText paragraph={true} lineCount={3} />;

  const uploadButton = (
    <Button kind="primary" size="md" onClick={() => setUploadModalOpen(true)}>
      Upload data
    </Button>
  );

  const uploadModal = (
    <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load modal" />}>
      <Suspense fallback={null}>
        <UploadDataModal
          open={uploadModalOpen}
          onClose={() => setUploadModalOpen(false)}
          folderId={folderId}
          onUploadSuccess={(fileName, uploadId) => {
            setRecentUploads((prev) => [{ fileName, uploadId, uploadedAt: new Date() }, ...prev]);
            setUploadsPage(1);
          }}
        />
      </Suspense>
    </ErrorBoundary>
  );

  const uploadsStart = (uploadsPage - 1) * uploadsPageSize;
  const pagedUploads = recentUploads.slice(uploadsStart, uploadsStart + uploadsPageSize);

  const recentUploadsList = recentUploads.length > 0 ? (
    <div style={{ marginBottom: 'var(--cds-spacing-05)' }}>
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
            <StructuredListRow key={`${item.uploadId}-${String(i)}`}>
              <StructuredListCell>{item.fileName}</StructuredListCell>
              <StructuredListCell>
                <span style={{ fontWeight: 600, color: 'var(--cds-support-success)' }}>
                  #{item.uploadId}
                </span>
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
    </div>
  ) : null;

  if (!views || views.length === 0) return (
    <>
      <div style={{ marginBottom: 'var(--cds-spacing-05)' }}>{uploadButton}</div>
      {recentUploadsList}
      <p>No views configured</p>
      {uploadModal}
    </>
  );

  const dropdownItems = views.map((v: View) => ({
    id: String(v.id),
    text: v.name,
  }));

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 'var(--cds-spacing-03)', marginBottom: 'var(--cds-spacing-05)' }}>
        {uploadButton}
        <div style={{ maxWidth: '300px', flex: 1 }}>
          <Dropdown
            id="view-selector"
            titleText="View"
            label="Select a view"
            items={dropdownItems}
            selectedItem={selectedView ? { id: String(selectedView.id), text: selectedView.name } : undefined}
            itemToString={(item: { id: string; text: string }) => item?.text ?? ''}
            onChange={({ selectedItem }: { selectedItem: { id: string; text: string } | null }) => {
              setSelectedViewId(selectedItem ? Number(selectedItem.id) : null);
            }}
          />
        </div>
        <Button
          kind="ghost"
          size="md"
          onClick={() => {
            const latestView = views?.find((v: View) => v.id === selectedView?.id) ?? selectedView;
            setEditingView(latestView);
            setModalKey((k) => k + 1);
            setConfigModalOpen(true);
          }}
        >
          Configure
        </Button>
        <Button
          kind="ghost"
          size="md"
          onClick={() => { setEditingView(null); setModalKey((k) => k + 1); setConfigModalOpen(true); }}
        >
          New View
        </Button>
      </div>
      {recentUploadsList}
      {selectedView && (!selectedView.components || selectedView.components.length === 0) && (
        <p style={{ opacity: 0.7 }}>
          This view has no columns configured. Click <strong>Configure</strong> to select which nodes to display.
        </p>
      )}
      {selectedView && selectedView.components && selectedView.components.length > 0 && (
        <ViewDataTable
          key={`${String(selectedView.id)}-${String(selectedView.components?.length ?? 0)}-${selectedView.components?.map(c => String(c.nodeId)).join(',') ?? ''}`}
          folderId={folderId}
          view={selectedView}
        />
      )}
      <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load modal" />}>
        <Suspense fallback={<SkeletonText paragraph={true} lineCount={3} />}>
          <ViewConfigModal
            key={modalKey}
            open={configModalOpen}
            onClose={() => setConfigModalOpen(false)}
            folderId={folderId}
            groupId={groupId}
            view={editingView}
          />
        </Suspense>
      </ErrorBoundary>
      {uploadModal}
    </div>
  );
};
