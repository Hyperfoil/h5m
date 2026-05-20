import type { FolderSummary } from '@client/types.gen.ts';

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, describe, expect, it, vi } from 'vitest';

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

const mockSummaries: FolderSummary[] = [
  {
    id: 1,
    name: 'quarkus-spring-boot',
    uploadCount: 100,
    nodeCount: 24,
    changeCount: 3,
    lastUpload: '2026-05-15T14:30:00' as unknown as undefined,
    lastChange: '2026-05-15T14:25:00' as unknown as undefined,
  },
  {
    id: 2,
    name: 'rhivos-perf',
    uploadCount: 50,
    nodeCount: 135,
    changeCount: 0,
    lastUpload: '2026-05-14T10:00:00' as unknown as undefined,
    lastChange: undefined,
  },
];

vi.mock('@client/@tanstack/react-query.gen.ts', () => ({
  getDashboardSummariesOptions: () => ({
    queryKey: ['dashboard'],
    queryFn: () => mockSummaries,
  }),
  listFoldersOptions: () => ({
    queryKey: ['listFolders'],
    queryFn: () => [],
  }),
}));

const { DashboardPage } = await import('@app/pages/DashboardPage');

function renderDashboard(summaries: FolderSummary[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(['dashboard'], summaries);

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('<DashboardPage />', () => {
  it('renders dashboard heading', async () => {
    renderDashboard(mockSummaries);
    await waitFor(() => {
      expect(screen.getByText('Dashboard')).toBeDefined();
    });
    cleanup();
  });

  it('shows folder names', async () => {
    renderDashboard(mockSummaries);
    await waitFor(() => {
      expect(screen.getByText('quarkus-spring-boot')).toBeDefined();
      expect(screen.getByText('rhivos-perf')).toBeDefined();
    });
    cleanup();
  });

  it('shows summary card labels', async () => {
    renderDashboard(mockSummaries);
    await waitFor(() => {
      expect(screen.getByText('Folders')).toBeDefined();
      expect(screen.getByText('Total Uploads')).toBeDefined();
      expect(screen.getByText('Changes Detected')).toBeDefined();
      expect(screen.getByText('Folders with Changes')).toBeDefined();
    });
    cleanup();
  });

  it('shows status tags', async () => {
    renderDashboard(mockSummaries);
    await waitFor(() => {
      expect(screen.getByText('Changes detected')).toBeDefined();
      expect(screen.getByText('Active')).toBeDefined();
    });
    cleanup();
  });

  it('shows empty state when no folders', async () => {
    renderDashboard([]);
    await waitFor(() => {
      expect(screen.getByText(/No folders configured/)).toBeDefined();
    });
    cleanup();
  });
});
