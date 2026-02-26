import { AppHeader } from '@app/layout/AppHeader';
import { createBrowserRouter } from 'react-router-dom';

const router = createBrowserRouter([
  {
    Component: AppHeader,
    path: '*',
  },
]);

export default router;
