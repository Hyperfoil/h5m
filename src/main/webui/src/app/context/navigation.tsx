import { User } from 'oidc-client-ts';
import { NavigateFunction } from 'react-router-dom';

let appNavigator: NavigateFunction | undefined = undefined;

export const setAppNavigator = (nav?: NavigateFunction) => {
  appNavigator = nav;
};

export const navigateSigninLocation = (user: undefined | User) => {
  const targetPath = (user?.state as { path?: string } | undefined)?.path;
  if (targetPath) {
    if (appNavigator) {
      return appNavigator(targetPath);
    } else {
      window.history.replaceState({}, document.title, targetPath);
    }
  }
};

export const navigateSignoutLocation = () => {
  if (appNavigator) {
    return appNavigator('/');
  } else {
    window.history.replaceState({}, document.title, '/');
  }
};
