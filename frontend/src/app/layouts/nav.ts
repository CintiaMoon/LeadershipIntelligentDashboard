import { canManageAssessments, isPlatformAdmin, isRespondent, type Principal } from '@/services/auth/principal';

export type NavIcon = 'dashboard' | 'compare' | 'tenants' | 'questionnaires' | 'runs' | 'people' | 'answer';

export interface NavItem {
  to: string;
  label: string;
  icon: NavIcon;
}

/**
 * The sidebar entries, one place for all of them. Only screens that exist are listed, and only the ones the role may use: the
 * backend refuses everything else, this only avoids showing a door that is locked (decision D51). The platform administrator
 * sees the tenants and nothing tenant-scoped; a respondent sees their own assessments and nothing else.
 */
export function navItems(principal: Principal | null): NavItem[] {
  if (isPlatformAdmin(principal)) {
    return [{ to: '/platform/tenants', label: 'Tenants', icon: 'tenants' }];
  }
  if (isRespondent(principal)) {
    return [{ to: '/respond', label: 'My assessments', icon: 'answer' }];
  }
  const items: NavItem[] = [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/compare', label: 'Compare', icon: 'compare' },
  ];
  if (canManageAssessments(principal)) {
    items.push(
      { to: '/questionnaires', label: 'Questionnaires', icon: 'questionnaires' },
      { to: '/assessment-runs', label: 'Assessment runs', icon: 'runs' },
      { to: '/people', label: 'People', icon: 'people' },
    );
  }
  return items;
}
