import {
  ErrorBoundary,
  Header,
  HeaderContainer,
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
import { Suspense } from 'react';

const NavFolders = () => {
  const { data: folders } = useSuspenseQuery(listFoldersOptions());
  return (
    <SideNavItems>
      {folders.map((folder) => (
        <SideNavLink key={folder.id} href={`/${String(folder.id)}`}>
          {folder.name}
        </SideNavLink>
      ))}
    </SideNavItems>
  );
};

export const AppHeader = () => {
  return (
    <Theme theme="g100">
      <HeaderContainer
        render={({ isSideNavExpanded, onClickSideNavExpand }) => (
          <>
            <Header aria-label="Carbon App">
              <SkipToContent />
              <HeaderMenuButton aria-label="Hamburger menu" onClick={onClickSideNavExpand} isActive={isSideNavExpanded} isCollapsible={true} />
              <HeaderName prefix="h5m">Horreum</HeaderName>
              <HeaderGlobalBar />
            </Header>
            <SideNav aria-label="Side navigation" expanded={isSideNavExpanded} isPersistent={false} isFixedNav={false}>
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
          </>
        )}
      />
    </Theme>
  );
};
