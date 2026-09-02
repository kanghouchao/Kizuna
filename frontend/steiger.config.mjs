import { defineConfig } from 'steiger';
import fsd from '@feature-sliced/steiger-plugin';

export default defineConfig([
  ...fsd.configs.recommended,
  {
    rules: {
      // FSD の pages 層は Next.js の予約ディレクトリ (src/pages) と衝突するため、FSD 公式の
      // Next.js 適配どおり _pages と命名している。このルールはその先頭の _ を綴り誤りと誤検知する
      'fsd/typo-in-layer-name': 'off',
      // _pages の slice は所属コンソール／ポータルを表す作用域接頭辞（platform-* / store-* を主に、
      // cast-* / member-* も同じ規則）で切り、app 層のルート群と対応させる設計のため、
      // 接頭辞の繰り返しはむしろ意図どおり
      'fsd/repetitive-naming': 'off',
      // このルールは app 層（Next の薄殻 re-export）からの参照をカウントしないため、
      // 「ページ slice は薄殻からのみ参照される」という本構成では全 slice が誤検知になる。恒久 off
      'fsd/insignificant-slice': 'off',
    },
  },
  {
    files: ['./src/_pages/**'],
    rules: {
      // _pages の slice 数は画面数そのもの（1 画面 = 1 slice、作用域接頭辞で所属を切る設計）で、
      // 閾値超過はまとめ方の誤りではなく画面が増えた事実を映しているだけ。
      // グループ化は Next のルート殻との対応を壊すため取らない。他層では有効なまま残す。
      'fsd/excessive-slicing': 'off',
    },
  },
]);
