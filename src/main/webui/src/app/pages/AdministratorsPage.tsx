import { AdministratorsPanel } from '@app/components/team/AdministratorsPanel';
import { Column, ErrorBoundary, Grid, InlineLoading, SkeletonText } from '@carbon/react';
import { Suspense } from 'react';

export const AdministratorsPage = () => (
  <ErrorBoundary fallback={<InlineLoading status="error" description="Failed to load administrators" />}>
    <Grid fullWidth>
      <Column lg={{ span: 6, offset: 5 }} md={{ span: 6, offset: 1 }} sm={4}>
        <Suspense fallback={<SkeletonText paragraph lineCount={5} />}>
          <AdministratorsPanel />
        </Suspense>
      </Column>
    </Grid>
  </ErrorBoundary>
);
