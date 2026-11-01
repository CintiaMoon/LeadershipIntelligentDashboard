import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// Client-side state only: which organization the user is looking at. Scoring data is never stored here.
interface SessionState {
  organizationId: string | null;
  setOrganization: (organizationId: string | null) => void;
}

export const useSession = create<SessionState>()(
  persist(
    (set) => ({
      organizationId: null,
      setOrganization: (organizationId) => set({ organizationId }),
    }),
    { name: 'lis.session', storage: createJSONStorage(() => window.sessionStorage) },
  ),
);
