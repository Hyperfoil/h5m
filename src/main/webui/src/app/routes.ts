import { AppHeader } from '@app/layout/AppHeader';
import { AdministratorsPage } from '@app/pages/AdministratorsPage';
import { DashboardPage } from '@app/pages/DashboardPage';
import { FolderPage } from '@app/pages/FolderPage';
import { SitePage } from '@app/pages/SitePage';
import { TeamManagementPage } from '@app/pages/TeamManagementPage';
import { TeamsPage } from '@app/pages/TeamsPage';
import { createBrowserRouter } from 'react-router-dom';

const router = createBrowserRouter([
  {
    Component: SitePage,
    path: '/help/*',
  },
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
      {
        Component: TeamManagementPage,
        path: 'team/:teamId',
      },
      {
        Component: TeamsPage,
        path: 'teams',
      },
      {
        Component: AdministratorsPage,
        path: 'administrators',
      },
    ],
  },
]);

export default router;
