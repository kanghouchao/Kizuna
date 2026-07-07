import { defineConfig, devices } from '@playwright/test';
import { defineBddConfig } from 'playwright-bdd';

// 日本語 Gherkin（各 .feature の `# language: ja`）で記述したシナリオを
// steps 定義と突き合わせてテストコードへ生成する。
const testDir = defineBddConfig({
  features: 'features/**/*.feature',
  steps: 'steps/**/*.ts',
});

// gateway の network alias 経由でフル実スタックへ到達する（既定は tenant ドメイン）。
const baseURL = process.env.BASE_URL || 'http://store1.kizuna.test';

export default defineConfig({
  testDir,
  outputDir: 'test-results',
  // list レポーターが各シナリオ名を標準出力へ 1 行ずつ出す（受け入れ基準のログ確認用）。
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL,
    headless: true,
    // 失敗時のみ trace / screenshot を成果物として残す。
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
