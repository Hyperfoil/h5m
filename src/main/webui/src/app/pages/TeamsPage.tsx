import type { Team } from '@client/types.gen.ts';

import { TeamMembersPanel } from '@app/components/team/TeamMembersPanel.tsx';
import { TeamsPanel } from '@app/components/team/TeamsPanel.tsx';
import { Column, ErrorBoundary, Grid, InlineLoading, InlineNotification, SkeletonText } from '@carbon/react';
import { Suspense, useState } from 'react';

export const TeamsPage = () => {
  const [selectedTeam, setSelectedTeam] = useState<Team | null>(null);
  return (
    <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load teams" />}>
      <Suspense fallback={<SkeletonText paragraph lineCount={5} />}>
        <Grid fullWidth>
          <Column lg={{ span: 4, offset: 3 }} md={3} sm={4}>
            <TeamsPanel onSelect={setSelectedTeam} />
          </Column>
          <Column lg={6} md={5} sm={4}>
            {selectedTeam ? (
              <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load members" />}>
                <Suspense fallback={<SkeletonText paragraph lineCount={5} />}>
                  <TeamMembersPanel key={selectedTeam.id} team={selectedTeam} />
                </Suspense>
              </ErrorBoundary>
            ) : (
              <InlineNotification kind="info" lowContrast hideCloseButton title="Select a team to manage members" />
            )}
          </Column>
        </Grid>
      </Suspense>
    </ErrorBoundary>
  );
};
