import { DEFAULT_NOTIFICATION_TIMEOUT, Notification, NotificationContext, NotificationKind, NotificationUpdate } from '@app/context/NotificationContext.tsx';
import { ToastNotification } from '@carbon/react';
import { ReactNode, useCallback, useEffect, useMemo, useReducer, useRef } from 'react';

const MAX_NOTIFICATIONS = 10;

type Action = { type: 'add'; notification: Notification } | { type: 'update'; id: number; fields: NotificationUpdate } | { type: 'remove'; id: number };

function reducer(state: Notification[], action: Action): Notification[] {
  switch (action.type) {
    case 'add': {
      const next = [action.notification, ...state];
      return next.length > MAX_NOTIFICATIONS ? next.slice(0, MAX_NOTIFICATIONS) : next;
    }
    case 'update':
      return state.map((n) => (n.id === action.id ? { ...n, ...action.fields } : n));
    case 'remove':
      return state.filter((n) => n.id !== action.id);
    default:
      return state;
  }
}

// extracts message from HTTP-Problems and Axios-like errors (response.data.detail for RFC 9457 Problem details, response.data.message) without coupling to Axios
export function extractErrorMessage(reason: unknown): string | undefined {
  if (typeof reason === 'string') return reason;
  if (typeof reason !== 'object' || reason === null) return undefined;

  const response = (reason as Record<string, unknown>).response;
  if (typeof response === 'object' && response !== null) {
    const data = (response as Record<string, unknown>).data;
    if (typeof data === 'object' && data !== null) {
      const body = data as Record<string, unknown>;
      if (typeof body.detail === 'string') return body.detail;
      if (Array.isArray(body.violations)) {
        return body.violations.map((v: { field?: string; message?: string }) => `${v.field}: ${v.message}`).join('; ');
      }
      if (typeof body.message === 'string') return body.message;
      if (typeof body.title === 'string') return body.title;
    }
  }

  const message = (reason as Record<string, unknown>).message;
  return typeof message === 'string' ? message : undefined;
}

export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const [notifications, dispatch] = useReducer(reducer, []);
  const notificationsRef = useRef<Notification[]>([]);
  useEffect(() => {
    notificationsRef.current = notifications;
  }, [notifications]);
  const nextId = useRef(0);

  const remove = useCallback((id: number) => {
    dispatch({ type: 'remove', id });
  }, []);

  const add = useCallback((kind: NotificationKind, title: string, subtitle?: string, timeout?: number): number => {
    // dedup logic that skips the add if a notification with the same kind + title + subtitle already exists in the array
    const existing = notificationsRef.current.find((n) => n.kind === kind && n.title === title && n.subtitle === subtitle);
    if (existing) {
      return existing.id;
    }
    const id = nextId.current++;
    dispatch({
      type: 'add',
      notification: {
        id,
        kind,
        title,
        subtitle,
        timeout: timeout ?? (kind === 'error' ? 0 : DEFAULT_NOTIFICATION_TIMEOUT),
      },
    });
    return id;
  }, []);

  const update = useCallback((id: number, fields: NotificationUpdate) => {
    dispatch({ type: 'update', id, fields });
  }, []);

  const info = useCallback((title: string) => add('info', title), [add]);
  const success = useCallback((title: string) => add('success', title), [add]);
  const warning = useCallback((title: string) => add('warning', title), [add]);
  const error = useCallback((title: string, subtitle?: string) => add('error', title, subtitle), [add]);
  const handleError = useCallback((title: string, reason: unknown) => add('error', title, extractErrorMessage(reason)), [add]);

  const contextValue = useMemo(
    () => ({
      add,
      info,
      success,
      warning,
      error,
      handleError,
      update,
      remove,
    }),
    [add, info, success, warning, error, handleError, update, remove],
  );

  return (
    <NotificationContext.Provider value={contextValue}>
      {children}
      <div className="notification-container">
        {notifications.map((n) => (
          <ToastNotification
            key={n.id}
            kind={n.kind}
            title={n.title}
            subtitle={n.subtitle}
            timeout={n.timeout}
            onClose={() => {
              remove(n.id);
            }}
            lowContrast
          >
            {n.children}
          </ToastNotification>
        ))}
      </div>
    </NotificationContext.Provider>
  );
};
