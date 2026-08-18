import { setAppNavigator } from '@app/context/navigation.tsx';
import { useNotification } from '@app/context/useNotification.tsx';
import { Login, Logout, UserAvatar } from '@carbon/icons-react';
import { HeaderGlobalAction, HeaderPanel, SideNavDivider, SideNavItems, SideNavLink, SideNavMenuItem } from '@carbon/react';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from 'react-oidc-context';
import { useLocation, useNavigate } from 'react-router-dom';

export const AuthActions = () => {
  const auth = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const { warning, handleError } = useNotification();
  const [panelOpen, setPanelOpen] = useState(false);

  useEffect(() => {
    setAppNavigator(navigate);
  }, [navigate]);

  const handleLogout = useCallback(() => {
    auth
      .signoutRedirect()
      .then(() => auth.removeUser())
      .catch((error: unknown) => {
        handleError('Logout failed', error);
      });
  }, [auth, handleError]);

  useEffect(() => {
    const handleAccessTokenExpiring = () => {
      auth.signinSilent({ extraQueryParams: { login_hint: auth.user?.profile.sub ?? '' } }).catch((error: unknown) => {
        handleError('Token renewal failed', error);
        handleLogout();
      });
    };
    const handleSilentRenewError = (error: unknown) => {
      warning('Session expired');
      handleError('Silent renewal failed', error);
      handleLogout();
    };
    auth.events.addAccessTokenExpiring(handleAccessTokenExpiring);
    auth.events.addSilentRenewError(handleSilentRenewError);
    return () => {
      auth.events.removeAccessTokenExpiring(handleAccessTokenExpiring);
      auth.events.removeSilentRenewError(handleSilentRenewError);
    };
  }, [auth, handleLogout, handleError, warning]);

  const handleLogin = useCallback(() => {
    const loginHintParam = auth.user ? { login_hint: auth.user.profile.sub } : undefined;
    auth.signinRedirect({ ...loginHintParam, state: { path: `${location.pathname}${location.search}` } }).catch((error: unknown) => {
      handleError('Login failed', error);
    });
  }, [auth, location, handleError]);

  const username = auth.user?.profile.name ?? auth.user?.profile.preferred_username ?? auth.user?.profile.upn?.toString() ?? 'Anonymous';

  if (!auth.isAuthenticated) {
    return (
      <HeaderGlobalAction aria-label="Login" onClick={handleLogin} tooltipAlignment="end">
        <Login size={24} />
      </HeaderGlobalAction>
    );
  }

  return (
    <>
      <HeaderGlobalAction
        aria-label={username}
        isActive={panelOpen}
        tooltipAlignment="end"
        onClick={() => {
          setPanelOpen((prev) => !prev);
        }}
      >
        <UserAvatar size={24} />
      </HeaderGlobalAction>
      <HeaderPanel aria-label="User panel" expanded={panelOpen}>
        <SideNavItems>
          <SideNavMenuItem>{username}</SideNavMenuItem>
          <SideNavDivider />
          <SideNavLink renderIcon={Logout} onClick={handleLogout}>
            Logout
          </SideNavLink>
        </SideNavItems>
      </HeaderPanel>
    </>
  );
};
