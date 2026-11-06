import js from '@eslint/js';
import nextPlugin from '@next/eslint-plugin-next';
import reactPlugin from 'eslint-plugin-react';
import hooksPlugin from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';
import globals from 'globals';

// 通知は語義層を通す。toast マネージャへ直接届くと、呼び出し側が段・色・duration を自分で
// 書けてしまい、段の体系を黙って抜けられる。塞ぐのは `toast` サブパスだけ——`@base-ui/react`
// の他のプリミティブは shared/ui が使い続ける。
const TOAST_PATTERN = {
  group: ['@base-ui/react/toast'],
  message: '通知は @/shared/notify の notify.* を通す。段の一覧は DESIGN.md。',
};

// ダイアログの外枠は DialogFrame だけが持つ。DialogContent を直接使うと幅の段と題名帯・操作帯の
// 固定を抜けられる。AlertDialog 系（ConfirmDialog）は別部品なので対象外。
const DIALOG_CONTENT_PATTERN = {
  group: ['@/shared/ui', '@/shared/ui/dialog'],
  importNames: ['DialogContent'],
  message:
    'ダイアログの外枠は @/shared/ui の DialogFrame を使う。幅の段の選び方は DESIGN.md の「モーダル」。',
};

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
  // この規則が見るのは静的な `import` と `export … from` だけ。動的 `import()` や `require()`、
  // `jest.mock` の文字列は素通りするので、そこは規約とレビューで見る。
  {
    rules: {
      'no-restricted-imports': ['error', { patterns: [TOAST_PATTERN, DIALOG_CONTENT_PATTERN] }],
    },
  },
  // flat config は後の設定が規則の選択肢を丸ごと置き換えるので、shared/ui には toast の制限だけを
  // 改めて渡す。外枠部品自身が DialogContent を使うのはここ。
  {
    files: ['src/shared/ui/**'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [TOAST_PATTERN] }],
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
