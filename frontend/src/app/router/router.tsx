import type { ComponentType } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppLayout } from '@/app/layouts/AppLayout';
import { AcceptInvitationPage } from '@/features/account/AcceptInvitationPage';
import { AccountPage } from '@/features/account/AccountPage';
import { CompanyPickerPage } from '@/features/account/CompanyPickerPage';
import { ForgotPasswordPage, ResetPasswordPage } from '@/features/account/PasswordResetPages';
import { RegisterPage } from '@/features/account/RegisterPage';
import { VerifyEmailPage } from '@/features/account/VerifyEmailPage';
import { CallbackPage } from '@/features/auth/CallbackPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotFoundPage } from '@/features/errors/NotFoundPage';
import { authConfig } from '@/services/auth/config';
import { companyBase, companyFromPath } from './company-path';
import { HomeRedirect, RequireAuth, RequireCompany, RequireConfigurationReader, RequireManager, RequirePlatformAdmin, RequireRespondent } from './guards';

/** Wraps a page so that only owners and administrators see it; the backend refuses everyone else as well. */
const manager = (Page: ComponentType) => function ManagerPage() { return (<RequireManager><Page /></RequireManager>); };
const reader = (Page: ComponentType) => function ReaderPage() { return (<RequireConfigurationReader><Page /></RequireConfigurationReader>); };
const respondent = (Page: ComponentType) => function RespondentPage() { return (<RequireRespondent><Page /></RequireRespondent>); };

/** The pages of the application's own accounts (decisions D65 to D73); they belong to no company. */
function accountRoutes(): RouteObject[] {
  return [
    { path: '/register', element: <RegisterPage /> },
    { path: '/verify-email', element: <VerifyEmailPage /> },
    { path: '/forgot-password', element: <ForgotPasswordPage /> },
    { path: '/reset-password', element: <ResetPasswordPage /> },
    { path: '/invitations/accept', element: <AcceptInvitationPage /> },
    { path: '/companies', element: (<RequireAuth><CompanyPickerPage /></RequireAuth>) },
    { path: '/account', element: (<RequireAuth><AccountPage /></RequireAuth>) },
  ];
}

/** Chosen when the page loads: the application's own accounts, or the external provider (kept until the last identity phase). */
export function buildRoutes(): RouteObject[] {
  const session = authConfig.mode === 'session';
  return [
    { path: '/login', element: <LoginPage /> },
    ...(session ? accountRoutes() : [{ path: '/auth/callback', element: <CallbackPage /> }]),
    ...appRoutes(),
  ];
}

function appRoutes(): RouteObject[] {
  return [
  {
    path: '/',
    element: (
      <RequireAuth>
        <RequireCompany>
          <AppLayout />
        </RequireCompany>
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
      { path: 'reports', lazy: async () => ({ Component: (await import('@/features/reports/ReportsPage')).ReportsPage }) },
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
      // members, API tokens and the security audit: the application's own accounts (decisions D70, D72, D74)
      { path: 'members', lazy: async () => ({ Component: manager((await import('@/features/members/MembersPage')).MembersPage) }) },
      { path: 'api-tokens', lazy: async () => ({ Component: manager((await import('@/features/apitokens/ApiTokensPage')).ApiTokensPage) }) },
      { path: 'security-audit', lazy: async () => ({ Component: manager((await import('@/features/audit/SecurityAuditPage')).SecurityAuditPage) }) },
      {
        path: 'platform/security-audit',
        lazy: async () => {
          const { SecurityAuditPage } = await import('@/features/audit/SecurityAuditPage');
          return { Component: () => (<RequirePlatformAdmin><SecurityAuditPage platform /></RequirePlatformAdmin>) };
        },
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  ];
}

export const routes: RouteObject[] = buildRoutes();

/**
 * The router for the address that was opened. The screens of a company are served under {@code /c/<company>}: that prefix is the
 * router's basename, so they keep their own short paths and every link inside them stays inside the company (decision D69).
 */
export const createRouter = () => {
  const company = companyFromPath(window.location.pathname);
  return createBrowserRouter(buildRoutes(), company ? { basename: companyBase(company) } : undefined);
};
