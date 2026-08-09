import js from '@eslint/js';
import nextPlugin from '@next/eslint-plugin-next';
import reactPlugin from 'eslint-plugin-react';
import hooksPlugin from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';
import globals from 'globals';

export default [
  {
    ignores: ['.next/**', 'node_modules/**', 'dist/**', 'build/**'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.jest,
      },
    },
    plugins: {
      '@next/next': nextPlugin,
      react: reactPlugin,
      'react-hooks': hooksPlugin,
    },
    rules: {
      ...reactPlugin.configs.recommended.rules,
      ...hooksPlugin.configs.recommended.rules,
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs['core-web-vitals'].rules,

      // Adjustments
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-require-imports': 'off',

      // Suppress specific React Hooks rules causing build failure on legacy patterns
      'react-hooks/error-boundaries': 'off',
      'react-hooks/set-state-in-effect': 'off',
    },
    settings: {
      react: {
        version: 'detect',
      },
    },
  },
  {
    files: ['**/*.js', '**/*.jsx', '**/*.mjs'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    // Spread disableTypeChecked config properties manually since extends is not supported
    ...tseslint.configs.disableTypeChecked,
    rules: {
      ...tseslint.configs.disableTypeChecked.rules,
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
  {
    files: ['**/*.cjs'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: {
        ...globals.node,
        require: 'readonly',
        module: 'readonly',
      },
    },
    ...tseslint.configs.disableTypeChecked,
    rules: {
      ...tseslint.configs.disableTypeChecked.rules,
      '@typescript-eslint/no-require-imports': 'off',
      '@typescript-eslint/no-var-requires': 'off',
      'no-undef': 'off', // Last resort if globals fail
    },
  },
  // 通知は語義層を通す。toast マネージャへ直接届くと、呼び出し側が段・色・duration を自分で
  // 書けてしまい、段の体系を黙って抜けられる。塞ぐのは `toast` サブパスだけ——`@base-ui/react`
  // の他のプリミティブは shared/ui が使い続ける。
  // この規則が見るのは静的な `import` と `export … from` だけ。動的 `import()` や `require()`、
  // `jest.mock` の文字列は素通りするので、そこは規約とレビューで見る。
  {
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@base-ui/react/toast'],
              message: '通知は @/shared/notify の notify.* を通す。段の一覧は DESIGN.md。',
            },
          ],
        },
      ],
    },
  },
  // 実体に触れてよいのは語義層と、それを描くプリミティブだけ。ディレクトリではなくファイルで
  // 挙げる——語義層は「生成はこの一箇所だけ」を不変条件に持つので、白名単もそう言う必要がある。
  // テストも例外にしない。例外が一つ増えるたびに、段を書かずに通知を出せる場所が一つ増える。
  {
    files: ['src/shared/notify/index.ts', 'src/shared/ui/toast.tsx'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
];
