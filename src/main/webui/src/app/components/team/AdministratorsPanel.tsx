import { useNotification } from '@app/context/useNotification.tsx';
import { useRoles } from '@app/context/useRoles.tsx';
import { Add, Subtract } from '@carbon/icons-react';
import { ContainedList, ContainedListItem, ExpandableSearch, IconButton, InlineNotification, Stack } from '@carbon/react';
import { addAdministratorMutation, listAdministratorsOptions, listUsersOptions, removeAdministratorMutation } from '@client/@tanstack/react-query.gen.ts';
import { useMutation, useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { useState } from 'react';

export const AdministratorsPanel = () => {
  const { isAdmin, userId } = useRoles();
  const queryClient = useQueryClient();
  const notifications = useNotification();
  const [filter, setFilter] = useState('');

  const { data: administrators } = useSuspenseQuery(listAdministratorsOptions());
  const { data: allUsers } = useSuspenseQuery(listUsersOptions());

  const administratorsQueryKey = listAdministratorsOptions().queryKey;
  const usersQueryKey = listUsersOptions().queryKey;

  const onSuccess = (data: typeof administrators) => {
    queryClient.setQueryData(administratorsQueryKey, data);
    void queryClient.invalidateQueries({ queryKey: usersQueryKey });
  };

  const addAdministrator = useMutation({
    ...addAdministratorMutation(),
    onSuccess,
    onError: (e) => {
      notifications.handleError('Failed to grant the Administrator role', e);
    },
  });

  const removeAdministrator = useMutation({
    ...removeAdministratorMutation(),
    onSuccess,
    onError: (e) => {
      notifications.handleError('Failed to revoke the Administrator role', e);
    },
  });

  const administratorIds = new Set(administrators.map((u) => u.id));
  const candidates = filter ? allUsers.filter((u) => !administratorIds.has(u.id) && (u.username ?? '').toLowerCase().includes(filter.toLowerCase())) : [];

  return (
    <Stack gap={2}>
      <ContainedList label="Administrators" kind="on-page">
        {administrators.map((user) => (
          <ContainedListItem
            key={user.id}
            action={
              user.id !== userId &&
              isAdmin && (
                <IconButton
                  label="Revoke Administrator"
                  kind={'danger--ghost' as 'ghost'}
                  onClick={() => {
                    removeAdministrator.mutate({ path: { userId: user.id ?? 0 } });
                  }}
                >
                  <Subtract />
                </IconButton>
              )
            }
          >
            {user.username ?? ''}
          </ContainedListItem>
        ))}
        {administrators.length === 0 && <InlineNotification kind="info" title="There are no administrators" lowContrast hideCloseButton />}
      </ContainedList>
      {isAdmin && (
        <ContainedList
          label="Add Administrators"
          kind="on-page"
          action={
            <ExpandableSearch
              id="administrator-search"
              size="lg"
              labelText="Search Users"
              placeholder="Search Users"
              value={filter}
              onChange={(e) => {
                setFilter(e.target.value);
              }}
              onClear={() => {
                setFilter('');
              }}
            />
          }
        >
          {candidates.map((user) => (
            <ContainedListItem
              key={user.id}
              action={
                <IconButton
                  label="Grant Administrator"
                  kind="ghost"
                  onClick={() => {
                    addAdministrator.mutate({ path: { userId: user.id ?? 0 } });
                  }}
                >
                  <Add />
                </IconButton>
              }
            >
              {user.username ?? ''}
            </ContainedListItem>
          ))}
        </ContainedList>
      )}
    </Stack>
  );
};
