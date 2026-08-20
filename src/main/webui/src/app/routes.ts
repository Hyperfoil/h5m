import { AppHeader } from '@app/layout/AppHeader';
import { DashboardPage } from '@app/pages/DashboardPage';
import { FolderPage } from '@app/pages/FolderPage';
import { createElement } from 'react';
import { createBrowserRouter } from 'react-router-dom';

const router = createBrowserRouter([
  {
    Component: AppHeader,
    path: '/',
    children: [
      {
        Component: DashboardPage,
        index: true,
      },
      {
        Component: () =>
          createElement('iframe', {
            src: '/site/docs/',
            title: 'Documentation',
            style: { display: 'block', border: 'none', width: '100%', height: 'calc(100vh - 3rem)' },
          }),
        path: 'help',
      },
      {
        Component: FolderPage,
        path: 'folder/:folderId',
      },
    ],
  },
]);

export default router;
