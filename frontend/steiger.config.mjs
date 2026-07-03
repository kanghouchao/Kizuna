import { defineConfig } from 'steiger';
import fsd from '@feature-sliced/steiger-plugin';

export default defineConfig([
  ...fsd.configs.recommended,
  {
    rules: {
      // FSD の pages 層は Next.js の予約ディレクトリ (src/pages) と衝突するため
      // _pages と命名しており（docs/ddd-fsd-refactor-plan.md D5）、このルールが誤検知する
      'fsd/typo-in-layer-name': 'off',
      // _pages の slice は作用域接頭辞（central-* / store-*）で切る設計判断（D6、ADR-0002 と対称）の
      // ため、接頭辞の繰り返しはむしろ意図どおり
      'fsd/repetitive-naming': 'off',
      // このルールは app 層（Next の薄殻 re-export）からの参照をカウントしないため、
      // 「ページ slice は薄殻からのみ参照される」という本構成では全 slice が誤検知になる。恒久 off
      'fsd/insignificant-slice': 'off',
    },
  },
]);
