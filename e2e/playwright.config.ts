import { defineConfig, devices } from '@playwright/test';
import { defineBddConfig } from 'playwright-bdd';
import { BASE_URL } from './base-url';

// 日本語 Gherkin（各 .feature の `# language: ja`）で記述したシナリオを
// steps 定義と突き合わせてテストコードへ生成する。
const testDir = defineBddConfig({
  features: 'features/**/*.feature',
  steps: 'steps/**/*.ts',
});

export default defineConfig({
  testDir,
  outputDir: 'test-results',
  // 実 dev スタックの単一テナントの可変状態（template_key）を共有するため直列実行で確定させる。
  fullyParallel: false,
  workers: 1,
  // 失敗時に 1 回だけ再試行し、trace: 'on-first-retry' を機能させる（cold スタックの一過性を吸収）。
  retries: 1,
  // list レポーターが各シナリオ名を標準出力へ 1 行ずつ出す（受け入れ基準のログ確認用）。
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    headless: true,
    // 失敗時のみ trace / screenshot を成果物として残す。
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
