import { AuthorizationProvider } from '@app/context/AuthorizationProvider.tsx';
import { navigateSigninLocation, navigateSignoutLocation } from '@app/context/navigation.tsx';
import { NotificationProvider } from '@app/context/NotificationProvider.tsx';
import router from '@app/routes.ts';
import { ErrorBoundary, GlobalTheme, Modal } from '@carbon/react';
import { TanStackDevtools } from '@tanstack/react-devtools';
import { formDevtoolsPlugin } from '@tanstack/react-form-devtools';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtoolsPanel } from '@tanstack/react-query-devtools';
import { FunctionComponent, useEffect } from 'react';
import { AuthProvider, AuthProviderProps } from 'react-oidc-context';
import { RouterProvider } from 'react-router-dom';
import '@app/carbon-styles.scss';
// load our styles last so that they override existing style
import '@app/App.css';

// noinspection JSUnusedGlobalSymbols
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10 * 1000, // ten seconds
      throwOnError: true,
    },
  },
});

const AuthFailureFallback = () => (
  <Modal
    danger
    open
    size="sm"
    modalHeading="Something went wrong"
    primaryButtonText="Retry"
    preventCloseOnClickOutside
    onRequestClose={() => {
      // Nothing to fall back to — reload is the only way out of this state.
      window.location.reload();
    }}
    onRequestSubmit={() => {
      window.location.reload();
    }}
  >
    <p>We couldn't reach the server. Please check your connection and retry.</p>
  </Modal>
);

const oidcConfig: AuthProviderProps = {
  authority: '/q/oidc',
  automaticSilentRenew: false,
  client_id: 'h5m-webui',
  client_secret: 'public',
  monitorSession: true,
  post_logout_redirect_uri: `${window.location.origin}/`,
  redirect_uri: `${window.location.origin}/`,
  scope: 'openid',
  silent_redirect_uri: `${window.location.origin}/oidc-silent-renew.html`,
};

export const App: FunctionComponent = () => {
  const carbonTheme = 'g90';

  useEffect(() => {
    document.documentElement.setAttribute('data-carbon-theme', carbonTheme);
  }, [carbonTheme]);

  return (
    <GlobalTheme theme={carbonTheme}>
      <QueryClientProvider client={queryClient}>
        <NotificationProvider>
          <AuthProvider {...oidcConfig} onRemoveUser={navigateSignoutLocation} onSigninCallback={navigateSigninLocation}>
            <ErrorBoundary fallback={<AuthFailureFallback />}>
              <AuthorizationProvider>
                <RouterProvider router={router} />
                <TanStackDevtools plugins={[formDevtoolsPlugin(), { name: 'TanStack Query', render: <ReactQueryDevtoolsPanel /> }]} />
              </AuthorizationProvider>
            </ErrorBoundary>
          </AuthProvider>
        </NotificationProvider>
      </QueryClientProvider>
    </GlobalTheme>
  );
};
