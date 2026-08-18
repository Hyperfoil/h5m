import { createContext } from 'react';

export interface AuthorizationContextInterface {
  isAdmin: boolean;
  isAuthenticated: boolean;
}

export const AuthorizationContext = createContext<AuthorizationContextInterface>({
  isAdmin: false,
  isAuthenticated: false,
});
