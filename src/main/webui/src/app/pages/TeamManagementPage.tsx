import { TeamMembersPanel } from '@app/components/team/TeamMembersPanel';
import { Column, ErrorBoundary, Grid, InlineLoading, SkeletonText } from '@carbon/react';
import { listTeamsOptions } from '@client/@tanstack/react-query.gen.ts';
import { useSuspenseQuery } from '@tanstack/react-query';
import { Suspense } from 'react';
import { useParams } from 'react-router-dom';

export const TeamManagementPage = () => {
  const { teamId } = useParams();
  const { data: teams } = useSuspenseQuery(listTeamsOptions());
  const team = teams.find((t) => t.id === Number(teamId));
  return (
    <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load team" />}>
      <Grid fullWidth>
        <Column lg={{ span: 6, offset: 5 }} md={{ span: 6, offset: 1 }} sm={4}>
          <Suspense fallback={<SkeletonText paragraph lineCount={5} />}>
            {team ? <TeamMembersPanel team={team} /> : <InlineLoading status="error" description="Team not found" />}
          </Suspense>
        </Column>
      </Grid>
    </ErrorBoundary>
  );
};
