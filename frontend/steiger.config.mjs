import { defineConfig } from 'steiger';
import fsd from '@feature-sliced/steiger-plugin';

export default defineConfig([
  ...fsd.configs.recommended,
  {
    rules: {
      // FSD の pages 層は Next.js の予約ディレクトリ (src/pages) と衝突するため
      // _pages と命名しており（docs/ddd-fsd-refactor-plan.md D5）、このルールが誤検知する
      'fsd/typo-in-layer-name': 'off',
      // ページ実装がまだ Next の app/ 配下にあり FSD の _pages 層に未移行のため、
      // entities への参照が「なし」と誤検知される。#200 でページ移行後に再有効化する
      'fsd/insignificant-slice': 'off',
      // _pages の slice は作用域接頭辞（central-* / store-*）で切る設計判断（D6、ADR-0002 と対称）の
      // ため、接頭辞の繰り返しはむしろ意図どおり
      'fsd/repetitive-naming': 'off',
    },
  },
]);
