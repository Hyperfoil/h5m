import router from '@app/routes.ts';
import { GlobalTheme } from '@carbon/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
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

const oidcConfig: AuthProviderProps = {
  authority: '/q/oidc',
  automaticSilentRenew: false,
  client_id: 'h5m-webui',
  client_secret: 'public',
  monitorSession: true,
  post_logout_redirect_uri: `${window.location.origin}/`,
  redirect_uri: `${window.location.origin}/`,
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
        <AuthProvider {...oidcConfig}>
          <RouterProvider router={router} />
          <ReactQueryDevtools />
        </AuthProvider>
      </QueryClientProvider>
    </GlobalTheme>
  );
};
