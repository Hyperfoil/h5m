import type { Team } from '@client/types.gen.ts';

import { AuthorizationContext } from '@app/context/AuthorizationContext.tsx';
import { listFoldersOptions } from '@client/@tanstack/react-query.gen.ts';
import { useQuery } from '@tanstack/react-query';
import { useContext } from 'react';

/**
 * Mirrors the backend's AuthorizationService.canModifyFolder rule: admins can modify
 * anything, unteamed resources are unrestricted, otherwise the caller must be a member
 * of the owning team. Fails closed when teams/loading state is unavailable.
 */
export const canModifyTeam = (teamId: number | null | undefined, isAdmin: boolean, teams: Team[]): boolean => {
  if (isAdmin) return true;
  if (teamId == null) return true;
  return teams.some((t) => t.id === teamId);
};

export const useFolderPermission = (folderId: number | undefined): boolean => {
  const { data: folders } = useQuery(listFoldersOptions());
  const { isAdmin, teams } = useContext(AuthorizationContext);
  const folder = folders?.find((f) => f.id === folderId);
  if (!folder) return false;
  return canModifyTeam(folder.teamId, isAdmin, teams);
};
