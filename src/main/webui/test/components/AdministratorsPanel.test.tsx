import type { User } from '@client/types.gen.ts';

import { cleanup, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

// jsdom doesn't have matchMedia or ResizeObserver — Carbon components need them
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

  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

const admin: User = { id: 1, username: 'alice', role: 'ADMIN', teamIds: [] };
const regular: User = { id: 2, username: 'bob', role: 'USER', teamIds: [] };

let administrators: User[] = [admin];

const mockAddAdministrator = vi.fn().mockResolvedValue([admin, { ...regular, role: 'ADMIN' }]);
const mockRemoveAdministrator = vi.fn().mockResolvedValue([admin]);

vi.mock('@client/@tanstack/react-query.gen.ts', () => ({
  listAdministratorsOptions: () => ({
    queryKey: ['listAdministrators'],
    queryFn: () => administrators,
  }),
  listUsersOptions: () => ({
    queryKey: ['listUsers'],
    queryFn: () => [admin, regular],
  }),
  addAdministratorMutation: () => ({
    mutationFn: (options: unknown) => mockAddAdministrator(options),
  }),
  removeAdministratorMutation: () => ({
    mutationFn: (options: unknown) => mockRemoveAdministrator(options),
  }),
}));

const { AdministratorsPanel } = await import('@app/components/team/AdministratorsPanel');
const { AuthorizationContext } = await import('@app/context/AuthorizationContext');
const { renderWithProviders } = await import('../test-utils');

function renderPanel({ isAdmin = true, userId = admin.id ?? 0 } = {}) {
  return renderWithProviders(
    <AuthorizationContext.Provider value={{ isAdmin, isAuthenticated: true, teams: [], userId }}>
      <AdministratorsPanel />
    </AuthorizationContext.Provider>,
  );
}

describe('<AdministratorsPanel />', () => {
  beforeEach(() => {
    administrators = [admin];
    mockAddAdministrator.mockClear();
    mockRemoveAdministrator.mockClear();
  });

  it('lists the current administrators', async () => {
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeDefined();
    });
    // non-administrators are not listed until searched for
    expect(screen.queryByText('bob')).toBeNull();

    cleanup();
  });

  it('grants the role to a user matching the search', async () => {
    renderPanel();
    await waitFor(() => {
      expect(screen.getByText('alice')).toBeDefined();
    });

    await userEvent.type(screen.getByPlaceholderText('Search Users'), 'bob');
    await waitFor(() => {
      expect(screen.getByText('bob')).toBeDefined();
    });
    await userEvent.click(screen.getByRole('button', { name: 'Grant Administrator' }));

    await waitFor(() => {
      expect(mockAddAdministrator).toHaveBeenCalledWith(expect.objectContaining({ path: { userId: regular.id } }));
    });

    cleanup();
  });

  it('revokes the role from another administrator', async () => {
    administrators = [admin, { ...regular, role: 'ADMIN' }];
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText('bob')).toBeDefined();
    });
    await userEvent.click(screen.getByRole('button', { name: 'Revoke Administrator' }));

    await waitFor(() => {
      expect(mockRemoveAdministrator).toHaveBeenCalledWith(expect.objectContaining({ path: { userId: regular.id } }));
    });

    cleanup();
  });

  it('does not offer to revoke the role of the current user', async () => {
    renderPanel({ userId: admin.id ?? 0 });

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeDefined();
    });
    expect(screen.queryByRole('button', { name: 'Revoke Administrator' })).toBeNull();

    cleanup();
  });

  it('hides the editing controls for non administrators', async () => {
    administrators = [admin, { ...regular, role: 'ADMIN' }];
    renderPanel({ isAdmin: false, userId: 3 });

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeDefined();
    });
    expect(screen.queryByPlaceholderText('Search Users')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Revoke Administrator' })).toBeNull();

    cleanup();
  });
});
