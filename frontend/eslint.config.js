import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

// The frontend never calculates canonical scoring results (frontend-spec 17.1, brief 12). Arithmetic on anything that
// looks like a score, penalty, contribution or confidence is only allowed in the presentation helpers.
const scoreName = '/[Ss]core|[Pp]enalt|[Cc]ontribution|[Cc]onfidence|[Dd]eduction/';

export default tseslint.config(
  { ignores: ['dist', 'src/types/api.d.ts', 'openapi'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx,mjs,js}'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    // end-to-end checks read free-form JSON from the real API
    files: ['e2e/**/*.ts'],
    rules: { '@typescript-eslint/no-explicit-any': 'off' },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/utils/presentation.ts', 'src/**/*.test.{ts,tsx}', 'src/tests/**'],
    rules: {
      // test fixtures and the mock API must never reach production code
      'no-restricted-imports': ['error', { patterns: [{ group: ['@/tests/*', '**/tests/*', 'msw', 'msw/*'], message: 'Test-only code must not be imported by production code.' }] }],
      'no-restricted-syntax': [
        'error',
        {
          selector: `BinaryExpression[operator=/^[-+*%/]$/]:has(MemberExpression[property.name=${scoreName}])`,
          message: 'Do not calculate scoring values in the frontend; show what the backend returned (utils/presentation only).',
        },
        {
          selector: `BinaryExpression[operator=/^[-+*%/]$/]:has(Identifier[name=${scoreName}])`,
          message: 'Do not calculate scoring values in the frontend; show what the backend returned (utils/presentation only).',
        },
      ],
    },
  },
);
