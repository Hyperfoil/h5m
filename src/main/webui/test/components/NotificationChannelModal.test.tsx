import type { NotificationChannel } from '@client/types.gen.ts';

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

const mockCreateChannel = vi.fn().mockResolvedValue({});
const mockUpdateChannel = vi.fn().mockResolvedValue({});

vi.mock('@client/@tanstack/react-query.gen', () => ({
  channelsOptions: (options: unknown) => ({ queryKey: ['channels', options] }),
  createChannelMutation: () => ({ mutationFn: (variables: unknown) => mockCreateChannel(variables) }),
  updateChannelMutation: () => ({ mutationFn: (variables: unknown) => mockUpdateChannel(variables) }),
}));

const { NotificationChannelModal } = await import('@app/components/notification/NotificationChannelModal');
const { renderWithProviders } = await import('../test-utils');

const webhookChannel: NotificationChannel = {
  id: 7,
  name: 'my-hook',
  enabled: true,
  template: 'a custom message',
  config: { method: 'WEBHOOK', url: 'https://example.com/hook' },
};

function renderModal(channel?: NotificationChannel) {
  return renderWithProviders(<NotificationChannelModal open onClose={vi.fn()} folderId={42} channel={channel ?? null} />);
}

/** The configuration step stays mounted on every step so that it can be validated on save — it is hidden instead. */
const isVisible = (element: HTMLElement) => element.closest('div[hidden]') === null;

const clickButton = async (user: ReturnType<typeof userEvent.setup>, name: string) => {
  await user.click(screen.getByRole('button', { name }));
};

describe('<NotificationChannelModal />', () => {
  beforeEach(() => {
    mockCreateChannel.mockClear();
    mockUpdateChannel.mockClear();
  });

  it('validates the configuration on save and returns to its step', async () => {
    const user = userEvent.setup();
    renderModal();

    // reach the template step without ever touching the URL: no field-level validator has run so far
    await clickButton(user, 'Next');
    await clickButton(user, 'Next');
    expect(isVisible(screen.getByLabelText('URL (required)'))).toBe(false);

    await clickButton(user, 'Save');

    await waitFor(() => {
      expect(isVisible(screen.getByLabelText('URL (required)'))).toBe(true);
    });
    expect(screen.getByLabelText('URL (required)').getAttribute('data-invalid')).not.toBeNull();
    expect(mockCreateChannel).not.toHaveBeenCalled();

    cleanup();
  });

  it('creates a channel when the configuration is valid', async () => {
    const user = userEvent.setup();
    renderModal();

    await clickButton(user, 'Next');
    await user.type(screen.getByLabelText('URL (required)'), 'https://example.com/hook');
    await clickButton(user, 'Next');
    await clickButton(user, 'Save');

    await waitFor(() => {
      expect(mockCreateChannel).toHaveBeenCalledTimes(1);
    });
    expect(mockCreateChannel.mock.calls[0]?.[0]).toMatchObject({
      query: { folderId: 42 },
      body: { config: { method: 'WEBHOOK', url: 'https://example.com/hook' } },
    });

    cleanup();
  });

  it('sends a cleared template so that it is not silently kept', async () => {
    const user = userEvent.setup();
    renderModal(webhookChannel);

    await clickButton(user, 'Next');
    await clickButton(user, 'Next');
    await user.clear(screen.getByLabelText('Message template (optional)'));
    await clickButton(user, 'Save');

    await waitFor(() => {
      expect(mockUpdateChannel).toHaveBeenCalledTimes(1);
    });
    expect(mockUpdateChannel.mock.calls[0]?.[0]).toMatchObject({ path: { id: 7 }, body: { name: 'my-hook', template: '' } });

    cleanup();
  });

  it('keeps the text being typed in a comma-separated list', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.selectOptions(screen.getByLabelText('Method'), 'EMAIL');
    await clickButton(user, 'Next');

    const recipients = screen.getByLabelText<HTMLInputElement>('Recipients (required)');
    await user.type(recipients, 'alice@example.com,');
    // a separator survives: re-deriving the text from the parsed list would render 'alice@example.com, '
    expect(recipients.value).toBe('alice@example.com,');

    await user.type(recipients, ' bob@example.com');
    await clickButton(user, 'Next');
    await clickButton(user, 'Save');

    await waitFor(() => {
      expect(mockCreateChannel).toHaveBeenCalledTimes(1);
    });
    expect(mockCreateChannel.mock.calls[0]?.[0]).toMatchObject({
      body: { config: { method: 'EMAIL', to: ['alice@example.com', 'bob@example.com'] } },
    });

    cleanup();
  });
});
