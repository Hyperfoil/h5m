import { AuthorizationContext } from '@app/context/AuthorizationContext.tsx';
import { currentUserOptions, userTeamsOptions } from '@client/@tanstack/react-query.gen.ts';
import { client } from '@client/client.gen.ts';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ReactNode, useEffect } from 'react';
import { useAuth } from 'react-oidc-context';

export const AuthorizationProvider = ({ children }: { children: ReactNode }) => {
  const auth = useAuth();
  const queryClient = useQueryClient();

  const token = auth.user?.access_token;
  const isAuthenticated = !!(auth.isAuthenticated && token);

  useEffect(() => {
    client.setConfig({ auth: isAuthenticated ? token : undefined });
    // Permission-bearing data (e.g. Folder.canModify) may have been fetched before the
    // token was attached above, or under a different identity — refetch it now that the
    // client carries the current one.
    void queryClient.invalidateQueries();
  }, [token, isAuthenticated, queryClient]);

  const { data: currentUser } = useQuery({
    ...currentUserOptions(),
    enabled: isAuthenticated,
    staleTime: 60 * 1000,
  });

  const { data: teams } = useQuery({
    ...userTeamsOptions(),
    enabled: isAuthenticated,
    staleTime: 60 * 1000,
  });

  return (
    <AuthorizationContext.Provider
      value={{
        isAdmin: isAuthenticated && currentUser?.role === 'ADMIN',
        isAuthenticated,
        teams: teams ?? [],
        userId: currentUser?.id ?? 0,
      }}
    >
      {children}
    </AuthorizationContext.Provider>
  );
};
