import { defineConfig, devices } from '@playwright/test';
import { defineBddProject } from 'playwright-bdd';
import { BASE_URL } from './base-url';

// 日本語 Gherkin（各 .feature の `# language: ja`）で記述したシナリオを
// steps 定義と突き合わせてテストコードへ生成する（生成先はプロジェクトごとに分かれる）。
// initial-setup は初期設定の導線でスイートのアカウントを作り、本体はそれを dependencies で待つ。
const initialSetup = defineBddProject({
  name: 'initial-setup',
  features: 'features/initial-setup/*.feature',
  // 他の steps の無条件 After は店長でログインしに行き、店長を作る前のシナリオを落とすため、自分のファイルに限る。
  steps: 'steps/initial-setup.steps.ts',
});

// features/*.feature は直下だけを拾い、features/initial-setup/ を含めない。
const main = defineBddProject({
  name: 'chromium',
  features: 'features/*.feature',
  steps: 'steps/**/*.ts',
});

export default defineConfig({
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
  projects: [
    {
      ...initialSetup,
      // 登録の成功後に落ちて再試行すると、従業員が既に居るため無関係な理由で再び落ちる。
      retries: 0,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      ...main,
      // 初期設定が作るアカウントを待つ。失敗したらこちらは実行されない。
      dependencies: ['initial-setup'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
