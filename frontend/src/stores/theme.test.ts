import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { initTheme, readStoredTheme, useTheme } from './theme';

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  useTheme.setState({ theme: 'dark' });
});

afterEach(() => vi.restoreAllMocks());

describe('theme', () => {
  it('is dark by default and leaves the page without a theme attribute', () => {
    expect(readStoredTheme()).toBe('dark');
    initTheme();
    expect(document.documentElement).not.toHaveAttribute('data-theme');
  });

  it('switches to light, marks the page and remembers the choice', () => {
    useTheme.getState().toggle();
    expect(useTheme.getState().theme).toBe('light');
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    expect(window.localStorage.getItem('lis.theme')).toBe('light');
    expect(readStoredTheme()).toBe('light');
  });

  it('switches back to dark and removes the attribute', () => {
    useTheme.getState().setTheme('light');
    useTheme.getState().toggle();
    expect(useTheme.getState().theme).toBe('dark');
    expect(document.documentElement).not.toHaveAttribute('data-theme');
  });

  it('applies the stored theme before the first render', () => {
    window.localStorage.setItem('lis.theme', 'light');
    useTheme.setState({ theme: readStoredTheme() });
    initTheme();
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });

  it('ignores a stored value it does not know', () => {
    window.localStorage.setItem('lis.theme', 'neon');
    expect(readStoredTheme()).toBe('dark');
  });

  it('still works when the browser refuses storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(readStoredTheme()).toBe('dark');
    useTheme.getState().setTheme('light');
    expect(useTheme.getState().theme).toBe('light');
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });
});
