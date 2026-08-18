import { AuthorizationContext } from '@app/context/AuthorizationContext.tsx';
import { roleOptions } from '@client/@tanstack/react-query.gen.ts';
import { client } from '@client/client.gen.ts';
import { useQuery } from '@tanstack/react-query';
import { ReactNode, useEffect } from 'react';
import { useAuth } from 'react-oidc-context';

export const AuthorizationProvider = ({ children }: { children: ReactNode }) => {
  const auth = useAuth();

  const token = auth.user?.access_token;
  const isAuthenticated = !!(auth.isAuthenticated && token);

  useEffect(() => {
    client.setConfig({ auth: isAuthenticated ? token : undefined });
  }, [token, isAuthenticated]);

  const { data: role } = useQuery({
    ...roleOptions(),
    enabled: isAuthenticated,
    staleTime: 60 * 1000,
  });

  return (
    <AuthorizationContext.Provider
      value={{
        isAdmin: isAuthenticated && role === 'ADMIN',
        isAuthenticated,
      }}
    >
      {children}
    </AuthorizationContext.Provider>
  );
};
