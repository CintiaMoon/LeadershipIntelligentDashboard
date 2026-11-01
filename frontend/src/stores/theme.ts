import { create } from 'zustand';

export type Theme = 'dark' | 'light';

const KEY = 'lis.theme';

/** Dark is the default (decision D51); the choice is a per-browser convenience, so every storage access is guarded. */
export function readStoredTheme(): Theme {
  try {
    return window.localStorage.getItem(KEY) === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
}

function apply(theme: Theme) {
  const root = document.documentElement;
  if (theme === 'light') {
    root.setAttribute('data-theme', 'light');
  } else {
    root.removeAttribute('data-theme');
  }
}

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggle: () => void;
}

export const useTheme = create<ThemeState>()((set, get) => ({
  theme: readStoredTheme(),
  setTheme: (theme) => {
    apply(theme);
    try {
      window.localStorage.setItem(KEY, theme);
    } catch {
      // the choice then lasts for this page only
    }
    set({ theme });
  },
  toggle: () => get().setTheme(get().theme === 'dark' ? 'light' : 'dark'),
}));

/** Applies the stored theme before the first render, so a returning user sees their theme from the start. */
export function initTheme(): void {
  apply(useTheme.getState().theme);
}
