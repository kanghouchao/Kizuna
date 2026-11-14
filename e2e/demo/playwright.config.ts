import { defineConfig } from '@playwright/test';
import { BASE_URL } from '../base-url';

// 目視用スタックへデータを投入するだけの設定。E2E の本体（../playwright.config.ts）とは別に実行する。
export default defineConfig({
  testDir: '.',
  testMatch: 'seed.spec.ts',
  outputDir: '../test-results/demo',
  // 空の DB への一度きりの投入で、途中まで書いた後の再試行は初期設定の重複で落ちる
  retries: 0,
  timeout: 300_000,
  reporter: [['list']],
  use: { baseURL: BASE_URL },
});
