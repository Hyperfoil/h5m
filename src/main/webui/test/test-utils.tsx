import type { Folder, NodeGroup } from '@client/types.gen.ts';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Creates a test QueryClient with pre-seeded data and no retries/refetching.
 */
export function createTestQueryClient(seedData?: Record<string, unknown>): QueryClient {
  const client = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: Infinity,
        gcTime: Infinity,
      },
    },
  });
  if (seedData) {
    for (const [key, value] of Object.entries(seedData)) {
      client.setQueryData(JSON.parse(key), value);
    }
  }
  return client;
}

interface TestWrapperOptions {
  queryClient?: QueryClient;
  initialRoute?: string;
}

/**
 * Wraps a component with QueryClientProvider and MemoryRouter for testing.
 */
export function createTestWrapper({ queryClient, initialRoute = '/' }: TestWrapperOptions = {}) {
  const client = queryClient ?? createTestQueryClient();
  return function TestWrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[initialRoute]}>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

/**
 * Render a component with test providers.
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: TestWrapperOptions & Omit<RenderOptions, 'wrapper'>,
) {
  const { queryClient, initialRoute, ...renderOptions } = options ?? {};
  return render(ui, {
    wrapper: createTestWrapper({ queryClient, initialRoute }),
    ...renderOptions,
  });
}

// === Mock data factories ===

export function createMockFolder(overrides?: Partial<Folder>): Folder {
  return {
    id: 1,
    name: 'test-folder',
    groupId: 1,
    ...overrides,
  };
}

export function createMockNodeGroup(overrides?: Partial<NodeGroup>): NodeGroup {
  return {
    id: 1,
    name: 'test-group',
    root: { id: 1, name: 'root', type: 'ROOT', sources: [] },
    sources: [
      { id: 2, name: 'cpu', type: 'JQ', operation: '.cpu', sources: [{ id: 1, name: 'root', type: 'ROOT' }] },
      { id: 3, name: 'mem', type: 'JQ', operation: '.mem', sources: [{ id: 1, name: 'root', type: 'ROOT' }] },
    ],
    ...overrides,
  };
}
