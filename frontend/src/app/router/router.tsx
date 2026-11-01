import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppLayout } from '@/app/layouts/AppLayout';
import { CallbackPage } from '@/features/auth/CallbackPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotFoundPage } from '@/features/errors/NotFoundPage';
import { HomeRedirect, RequireAuth, RequirePlatformAdmin } from './guards';

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
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
