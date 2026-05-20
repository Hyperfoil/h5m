import type { FolderSummary } from '@client/types.gen.ts';

import {
  Column,
  ErrorBoundary,
  Grid,
  InlineLoading,
  SkeletonText,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Tag,
  Tile,
} from '@carbon/react';
import { getDashboardSummariesOptions } from '@client/@tanstack/react-query.gen.ts';
import { useSuspenseQuery } from '@tanstack/react-query';
import { Suspense } from 'react';
import { useNavigate } from 'react-router-dom';

function formatDate(date?: string | null): string {
  if (!date) return '—';
  try {
    const d = new Date(date);
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
      + ' ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  } catch {
    return date;
  }
}

function folderStatus(summary: FolderSummary): { label: string; type: 'green' | 'red' | 'gray' } {
  if ((summary.changeCount ?? 0) > 0) {
    return { label: 'Changes detected', type: 'red' };
  }
  if ((summary.uploadCount ?? 0) > 0) {
    return { label: 'Active', type: 'green' };
  }
  return { label: 'No uploads', type: 'gray' };
}

const SummaryCards = ({ summaries }: { summaries: FolderSummary[] }) => {
  const totalUploads = summaries.reduce((sum, s) => sum + (s.uploadCount ?? 0), 0);
  const totalChanges = summaries.reduce((sum, s) => sum + (s.changeCount ?? 0), 0);
  const foldersWithChanges = summaries.filter((s) => (s.changeCount ?? 0) > 0).length;

  return (
    <Grid condensed style={{ marginBottom: 'var(--cds-spacing-05)' }}>
      <Column lg={4} md={2} sm={1}>
        <Tile>
          <div style={{ fontSize: '0.75rem', opacity: 0.7 }}>Folders</div>
          <div style={{ fontSize: '2rem', fontWeight: 'bold' }}>{summaries.length}</div>
        </Tile>
      </Column>
      <Column lg={4} md={2} sm={1}>
        <Tile>
          <div style={{ fontSize: '0.75rem', opacity: 0.7 }}>Total Uploads</div>
          <div style={{ fontSize: '2rem', fontWeight: 'bold' }}>{totalUploads}</div>
        </Tile>
      </Column>
      <Column lg={4} md={2} sm={1}>
        <Tile>
          <div style={{ fontSize: '0.75rem', opacity: 0.7 }}>Changes Detected</div>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: totalChanges > 0 ? 'var(--cds-support-error)' : undefined }}>
            {totalChanges}
          </div>
        </Tile>
      </Column>
      <Column lg={4} md={2} sm={1}>
        <Tile>
          <div style={{ fontSize: '0.75rem', opacity: 0.7 }}>Folders with Changes</div>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: foldersWithChanges > 0 ? 'var(--cds-support-error)' : undefined }}>
            {foldersWithChanges}
          </div>
        </Tile>
      </Column>
    </Grid>
  );
};

const FolderTable = ({ summaries }: { summaries: FolderSummary[] }) => {
  const navigate = useNavigate();

  return (
    <StructuredListWrapper selection>
      <StructuredListHead>
        <StructuredListRow head>
          <StructuredListCell head>Folder</StructuredListCell>
          <StructuredListCell head>Status</StructuredListCell>
          <StructuredListCell head>Uploads</StructuredListCell>
          <StructuredListCell head>Nodes</StructuredListCell>
          <StructuredListCell head>Changes</StructuredListCell>
          <StructuredListCell head>Last Upload</StructuredListCell>
          <StructuredListCell head>Last Change</StructuredListCell>
        </StructuredListRow>
      </StructuredListHead>
      <StructuredListBody>
        {summaries.map((summary) => {
          const status = folderStatus(summary);
          return (
            <StructuredListRow
              key={summary.id}
              onClick={() => void navigate(`/folder/${String(summary.id)}`)}
              style={{ cursor: 'pointer' }}
            >
              <StructuredListCell style={{ fontWeight: 'bold' }}>
                {summary.name}
              </StructuredListCell>
              <StructuredListCell>
                <Tag size="sm" type={status.type}>{status.label}</Tag>
              </StructuredListCell>
              <StructuredListCell>{summary.uploadCount}</StructuredListCell>
              <StructuredListCell>{summary.nodeCount}</StructuredListCell>
              <StructuredListCell>
                {(summary.changeCount ?? 0) > 0 ? (
                  <span style={{ color: 'var(--cds-support-error)', fontWeight: 'bold' }}>
                    {summary.changeCount}
                  </span>
                ) : (
                  '0'
                )}
              </StructuredListCell>
              <StructuredListCell>{formatDate(summary.lastUpload as unknown as string)}</StructuredListCell>
              <StructuredListCell>{formatDate(summary.lastChange as unknown as string)}</StructuredListCell>
            </StructuredListRow>
          );
        })}
      </StructuredListBody>
    </StructuredListWrapper>
  );
};

const DashboardContent = () => {
  const { data: summaries } = useSuspenseQuery(getDashboardSummariesOptions());

  if (summaries.length === 0) {
    return (
      <Tile>
        <p>No folders configured yet. Create a folder and upload data to get started.</p>
      </Tile>
    );
  }

  return (
    <>
      <SummaryCards summaries={summaries} />
      <FolderTable summaries={summaries} />
    </>
  );
};

export const DashboardPage = () => {
  return (
    <div style={{ padding: 'var(--cds-spacing-05)', marginTop: 'var(--cds-spacing-09)' }}>
      <h2 style={{ marginBottom: 'var(--cds-spacing-05)' }}>Dashboard</h2>
      <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load dashboard" />}>
        <Suspense fallback={<SkeletonText paragraph={true} lineCount={5} />}>
          <DashboardContent />
        </Suspense>
      </ErrorBoundary>
    </div>
  );
};
