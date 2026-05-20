import { AppHeader } from '@app/layout/AppHeader';
import { DashboardPage } from '@app/pages/DashboardPage';
import { FolderPage } from '@app/pages/FolderPage';
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
        Component: FolderPage,
        path: 'folder/:folderId',
      },
    ],
  },
]);

export default router;
