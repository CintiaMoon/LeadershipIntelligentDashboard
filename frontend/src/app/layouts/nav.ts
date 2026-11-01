export type NavIcon = 'dashboard' | 'compare' | 'tenants';

export interface NavItem {
  to: string;
  label: string;
  icon: NavIcon;
}

/**
 * The sidebar entries, one place for all of them. Only screens that exist are listed: nothing links to a feature that is not
 * there (decision D51). The platform administrator sees the tenants and nothing tenant-scoped.
 */
export function navItems(platformAdmin: boolean): NavItem[] {
  if (platformAdmin) {
    return [{ to: '/platform/tenants', label: 'Tenants', icon: 'tenants' }];
  }
  return [
    { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { to: '/compare', label: 'Compare', icon: 'compare' },
  ];
}
