import { AppHeader } from '@app/layout/AppHeader';
import { Content } from '@carbon/react';
import { CSSProperties } from 'react';
import { useLocation } from 'react-router-dom';

export const DOCS_IFRAME_STYLE: CSSProperties = { display: 'block', border: 'none', width: '100%', height: 'calc(100vh - 3rem)' };

export const docsIframeSrc = (pathname = '', hash = '') => {
  const path = pathname.replace(/^\/help\/?/, '');
  const needsSlash = path && !path.endsWith('/') && !path.includes('.');
  return `/site/${path}${needsSlash ? '/' : ''}${hash}`;
};

export const SitePage = () => {
  const { pathname, hash } = useLocation();
  return (
    <AppHeader>
      <Content style={{ padding: 0 }}>
        <iframe src={docsIframeSrc(pathname, hash)} title="Documentation" style={DOCS_IFRAME_STYLE} />
      </Content>
    </AppHeader>
  );
};
