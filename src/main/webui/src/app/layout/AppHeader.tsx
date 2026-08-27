import { AuthActions } from '@app/layout/AuthActions.tsx';
import { Help } from '@carbon/icons-react';
import {
  Content,
  ErrorBoundary,
  Header,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderMenuButton,
  HeaderName,
  InlineLoading,
  SideNav,
  SideNavItems,
  SideNavLink,
  SkeletonText,
  SkipToContent,
  Theme,
} from '@carbon/react';
import { listFoldersOptions } from '@client/@tanstack/react-query.gen.ts';
import { useSuspenseQuery } from '@tanstack/react-query';
import { Suspense, useCallback, useState } from 'react';
import { Link, Outlet, useNavigate, useParams } from 'react-router-dom';

const NavFolders = () => {
  const { data: folders } = useSuspenseQuery(listFoldersOptions());
  const { folderId } = useParams<{ folderId: string }>();
  return (
    <SideNavItems>
      {folders.map((folder) => (
        <SideNavLink key={folder.id} as={Link} to={`/folder/${String(folder.id)}`} isActive={folder.id === Number(folderId)}>
          {folder.name}
        </SideNavLink>
      ))}
    </SideNavItems>
  );
};

export const AppHeader = () => {
  const navigate = useNavigate();
  const [sideNavOpen, setSideNavOpen] = useState(false);
  const toggleSideNav = useCallback(() => {
    setSideNavOpen((prev) => !prev);
  }, []);
  return (
    <>
      <Theme theme="g100">
        <Header aria-label="Carbon App">
          <SkipToContent />
          <HeaderMenuButton aria-label="Hamburger menu" onClick={toggleSideNav} isActive={sideNavOpen} isCollapsible={true} />
          <HeaderName as={Link} to="/" prefix="h5m">
            Horreum
          </HeaderName>
          <HeaderGlobalBar>
            <HeaderGlobalAction aria-label="Documentation" onClick={() => void navigate('/help')} tooltipAlignment="end">
              <Help size={24} />
            </HeaderGlobalAction>
            <AuthActions />
          </HeaderGlobalBar>
        </Header>
        <SideNav aria-label="Side navigation" expanded={sideNavOpen} isPersistent={false} isFixedNav={false}>
          <ErrorBoundary
            fallback={
              <div style={{ padding: 'var(--cds-spacing-05)' }}>
                <InlineLoading status="error" description="Folder load failed" />
              </div>
            }
          >
            <Suspense
              fallback={
                <div style={{ padding: 'var(--cds-spacing-05)' }}>
                  <SkeletonText paragraph={true} lineCount={50} />
                </div>
              }
            >
              <NavFolders />
            </Suspense>
          </ErrorBoundary>
        </SideNav>
      </Theme>
      <Content style={{ padding: 0 }}>
        <Outlet />
      </Content>
    </>
  );
};
