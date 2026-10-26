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

// 分割と絞り込みは root の `task e2e` が環境変数で渡す（シナリオ名の空白や日本語をシェル引数に通さないため）。
const [shardCurrent, shardTotal] = (process.env.E2E_SHARD ?? '').split('/').map(Number);
const grep = process.env.E2E_GREP ? new RegExp(process.env.E2E_GREP) : undefined;

export default defineConfig({
  outputDir: 'test-results',
  // 実 dev スタックの単一テナントの可変状態（template_key）を共有するため直列実行で確定させる。
  fullyParallel: false,
  workers: 1,
  // 失敗時に 1 回だけ再試行し、trace: 'on-first-retry' を機能させる（cold スタックの一過性を吸収）。
  retries: 1,
  shard: shardTotal ? { current: shardCurrent, total: shardTotal } : null,
  // list レポーターが各シナリオ名を標準出力へ 1 行ずつ出す（受け入れ基準のログ確認用）。
  // CI の各 shard は blob を残し、最後の job が merge-reports で一つの HTML にまとめる。
  reporter: process.env.CI
    ? [['list'], ['blob']]
    : [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    headless: true,
    // 業務の「本日」・週の区切りは後端・ステップとも Asia/Tokyo で数える。ブラウザも合わせないと、
    // UTC と日付が食い違う時間帯（日本時間 0〜9 時）に週表示がずれる。
    timezoneId: 'Asia/Tokyo',
    // 失敗時のみ trace / screenshot を成果物として残す。
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      ...initialSetup,
      // 登録の成功後に落ちて再試行すると、従業員が既に居るため無関係な理由で再び落ちる。
      retries: 0,
      // 再試行が無いので on-first-retry では trace が残らない。失敗すると本体が実行されないため必ず残す。
      use: { ...devices['Desktop Chrome'], trace: 'retain-on-failure' },
    },
    {
      ...main,
      // 絞り込みは本体だけに掛ける。全体に掛けると initial-setup まで外れ、アカウントの無いまま走る。
      grep,
      // 初期設定が作るアカウントを待つ。失敗したらこちらは実行されない。
      dependencies: ['initial-setup'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
