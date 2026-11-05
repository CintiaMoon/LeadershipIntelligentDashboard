import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// Client-side state only: which organization the user is looking at. Scoring data is never stored here.
interface SessionState {
  organizationId: string | null;
  setOrganization: (organizationId: string | null) => void;
  /**
   * Organizations the user named in this tab that may not exist yet. A member who is not limited to a list may name a new
   * organization (they are created by first use), which is how the first one of a new company comes to exist.
   */
  extraOrganizations: string[];
  addOrganization: (organizationId: string) => void;
}

export const useSession = create<SessionState>()(
  persist(
    (set) => ({
      organizationId: null,
      setOrganization: (organizationId) => set({ organizationId }),
      extraOrganizations: [],
      addOrganization: (organizationId) =>
        set((s) => ({ extraOrganizations: s.extraOrganizations.includes(organizationId) ? s.extraOrganizations : [...s.extraOrganizations, organizationId], organizationId })),
    }),
    { name: 'lis.session', storage: createJSONStorage(() => window.sessionStorage) },
  ),
);
