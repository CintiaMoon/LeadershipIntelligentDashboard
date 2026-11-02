import type { ComponentType } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppLayout } from '@/app/layouts/AppLayout';
import { CallbackPage } from '@/features/auth/CallbackPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotFoundPage } from '@/features/errors/NotFoundPage';
import { HomeRedirect, RequireAuth, RequireConfigurationReader, RequireManager, RequirePlatformAdmin, RequireRespondent } from './guards';

/** Wraps a page so that only owners and administrators see it; the backend refuses everyone else as well. */
const manager = (Page: ComponentType) => function ManagerPage() { return (<RequireManager><Page /></RequireManager>); };
const reader = (Page: ComponentType) => function ReaderPage() { return (<RequireConfigurationReader><Page /></RequireConfigurationReader>); };
const respondent = (Page: ComponentType) => function RespondentPage() { return (<RequireRespondent><Page /></RequireRespondent>); };

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/auth/callback', element: <CallbackPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomeRedirect /> },
      // the dashboard carries the chart library, so it is loaded on demand
      { path: 'dashboard', lazy: async () => ({ Component: (await import('@/features/dashboard/DashboardPage')).DashboardPage }) },
      { path: 'runs/:runId/explanation', lazy: async () => ({ Component: (await import('@/features/explanation/ExplanationPage')).ExplanationPage }) },
      { path: 'runs/:runId/dimensions/:dimension', lazy: async () => ({ Component: (await import('@/features/dimensions/DimensionDetailPage')).DimensionDetailPage }) },
      { path: 'runs/:runId/patterns/:patternId', lazy: async () => ({ Component: (await import('@/features/patterns/PatternDetailPage')).PatternDetailPage }) },
      { path: 'runs/:runId/penalties/:penaltyId', lazy: async () => ({ Component: (await import('@/features/penalties/PenaltyDetailPage')).PenaltyDetailPage }) },
      { path: 'compare', lazy: async () => ({ Component: (await import('@/features/compare/ComparePage')).ComparePage }) },
      {
        path: 'platform/tenants',
        lazy: async () => {
          const { TenantListPage } = await import('@/features/platform/TenantListPage');
          return { Component: () => (<RequirePlatformAdmin><TenantListPage /></RequirePlatformAdmin>) };
        },
      },
      {
        path: 'platform/tenants/new',
        lazy: async () => {
          const { CreateTenantPage } = await import('@/features/platform/CreateTenantPage');
          return { Component: () => (<RequirePlatformAdmin><CreateTenantPage /></RequirePlatformAdmin>) };
        },
      },
      // questionnaires, the roster and assessment runs: owners and administrators (decisions D47, D48, D50)
      { path: 'questionnaires', lazy: async () => ({ Component: manager((await import('@/features/questionnaires/QuestionnaireListPage')).QuestionnaireListPage) }) },
      { path: 'questionnaires/new', lazy: async () => ({ Component: manager((await import('@/features/questionnaires/NewQuestionnairePage')).NewQuestionnairePage) }) },
      { path: 'questionnaires/:questionnaireId', lazy: async () => ({ Component: manager((await import('@/features/questionnaires/BuilderPage')).BuilderPage) }) },
      { path: 'people', lazy: async () => ({ Component: manager((await import('@/features/roster/PeoplePage')).PeoplePage) }) },
      { path: 'assessment-runs', lazy: async () => ({ Component: manager((await import('@/features/runs/RunListPage')).RunListPage) }) },
      { path: 'assessment-runs/new', lazy: async () => ({ Component: manager((await import('@/features/runs/StartRunPage')).StartRunPage) }) },
      { path: 'assessment-runs/:runId', lazy: async () => ({ Component: manager((await import('@/features/runs/RunDetailPage')).RunDetailPage) }) },
      // scoring configuration: layers, versions, the JSON editor, review and approval (decisions D52 to D58)
      { path: 'configuration', lazy: async () => ({ Component: reader((await import('@/features/configuration/ConfigurationPage')).ConfigurationPage) }) },
      ...(['platform', 'tenant'] as const).map((kind) => ({
        path: `configuration/layers/${kind}`,
        lazy: async () => {
          const { ConfigurationLayerPage } = await import('@/features/configuration/ConfigurationLayerPage');
          return { Component: reader(() => <ConfigurationLayerPage kind={kind} />) };
        },
      })),
      {
        path: 'configuration/layers/organization/:organizationId',
        lazy: async () => {
          const { ConfigurationLayerPage } = await import('@/features/configuration/ConfigurationLayerPage');
          return { Component: reader(() => <ConfigurationLayerPage kind="organization" />) };
        },
      },
      { path: 'configuration/versions/:versionId', lazy: async () => ({ Component: reader((await import('@/features/configuration/ConfigurationVersionPage')).ConfigurationVersionPage) }) },
      // the respondent flow
      { path: 'respond', lazy: async () => ({ Component: respondent((await import('@/features/respond/MyRunsPage')).MyRunsPage) }) },
      { path: 'respond/:runId', lazy: async () => ({ Component: respondent((await import('@/features/respond/RespondPage')).RespondPage) }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
