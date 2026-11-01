import type { ComponentType } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppLayout } from '@/app/layouts/AppLayout';
import { CallbackPage } from '@/features/auth/CallbackPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotFoundPage } from '@/features/errors/NotFoundPage';
import { HomeRedirect, RequireAuth, RequireManager, RequirePlatformAdmin, RequireRespondent } from './guards';

/** Wraps a page so that only owners and administrators see it; the backend refuses everyone else as well. */
const manager = (Page: ComponentType) => function ManagerPage() { return (<RequireManager><Page /></RequireManager>); };
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
      // the respondent flow
      { path: 'respond', lazy: async () => ({ Component: respondent((await import('@/features/respond/MyRunsPage')).MyRunsPage) }) },
      { path: 'respond/:runId', lazy: async () => ({ Component: respondent((await import('@/features/respond/RespondPage')).RespondPage) }) },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
