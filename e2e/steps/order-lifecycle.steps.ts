import { expect, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { cancelOrder, createCast, loginAsStoreAdmin, loginViaUiAndEnterStore } from './store-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());

// 播種した実体。受注を消す口は無い（ADR 0013）ので、後片付けは取消で終端へ送るだけ。
let createdCastId = '';
let createdCastName = '';
let createdOrderId = '';
// このシナリオの受注を共有の店舗の中で一意に指す鍵。お客様名に埋めてカードを名指す。
let customerName = '';
let storeId = '';

/** このシナリオが起こした受注のカード。共有の店舗なので、お客様名で自分の行だけを指す。 */
function ownCard(page: Page) {
  return page.getByRole('listitem').filter({ hasText: customerName });
}

Given('店舗コンソールへ入り受注一覧を開く', async ({ page, request }) => {
  storeId = await loginViaUiAndEnterStore(page);
  const token = await loginAsStoreAdmin(request);
  createdCastName = `受注ライフサイクル-${Date.now()}`;
  createdCastId = await createCast(request, token, createdCastName);
  customerName = `受注LC客-${Date.now()}`;
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders`);
  await expect(page.getByRole('heading', { name: 'オーダー一覧', exact: true })).toBeVisible();
});

When('電話受付の受注を登録する', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders/create`);
  await page.getByLabel('お客様名', { exact: true }).fill(customerName);
  await page.getByLabel('営業日', { exact: true }).fill(todayInTokyo());
  await page.getByLabel('人数', { exact: true }).fill('2');

  // 指名は候補から選ぶ（サーバ側が在籍中のキャストしか受け付けない）。名前で絞ってから選ぶのは、
  // 候補の読み口が件数上限を持つため — 共有の店舗ではキャストが積み上がり、絞らないと播種した
  // このキャストが上限の外へ押し出されて選べなくなる。
  await page.getByLabel('キャスト *', { exact: true }).click();
  await page.getByPlaceholder('名前で検索').fill(createdCastName);
  await page.getByRole('option', { name: createdCastName }).click();

  // 受付担当は選ばない。既定の「自分」＝項目ごと省略送信で、サーバが実行者本人に解決する
  const [response] = await Promise.all([
    page.waitForResponse(
      resp =>
        resp.url().endsWith('/api/store/orders') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: '登録する', exact: true }).click(),
  ]);
  expect(response.status()).toBe(201);
  createdOrderId = (await response.json()).id as string;
});

Then('登録した受注が「対応が要る」群に確定として現れる', async ({ page }) => {
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/?$`), { timeout: 15000 });
  // 電話口で受けると決めた時点で可否は判断済み。画面上でもう一度確定し直す段は無い
  await expect(ownCard(page).getByText('確定', { exact: true })).toBeVisible({ timeout: 15000 });
});

When('受注の編集モーダルを開き人数を {string} に直して保存する', async ({ page }, pax: string) => {
  await ownCard(page).getByRole('button', { name: '編集', exact: true }).click();
  const dialog = page.getByRole('dialog');
  // 開くたびに 1 件を読み直すので、播かれるまで待ってから書き換える
  await expect(dialog.getByLabel('人数', { exact: true })).toHaveValue('2', { timeout: 15000 });
  await dialog.getByLabel('人数', { exact: true }).fill(pax);
  await Promise.all([
    page.waitForResponse(
      resp =>
        resp.url().includes(`/api/store/orders/${createdOrderId}`) &&
        resp.request().method() === 'PUT',
      { timeout: 15000 }
    ),
    dialog.getByRole('button', { name: '保存', exact: true }).click(),
  ]);
});

Then('カードの内容が人数 {string} に変わる', async ({ page }, pax: string) => {
  // 保存が実 API へ届いていなければここで赤くなる（mock 保存の再発を防ぐ）
  await expect(ownCard(page)).toContainText(`${pax} 名`, { timeout: 15000 });
});

When(
  'カード内の二段で理由 {string} を書いて取消す',
  async ({ page }, reason: string) => {
    const card = ownCard(page);
    await card.getByRole('button', { name: '取消', exact: true }).click();
    // 検証では押せなくしない。理由なしで押すと欄の傍が理由を求める（DESIGN.md）
    await card.getByRole('button', { name: '取消する', exact: true }).click();
    await expect(card.getByText('取消の理由を入力してください')).toBeVisible();
    await card.getByLabel('取消の理由', { exact: true }).fill(reason);
    await Promise.all([
      page.waitForResponse(
        resp => resp.url().includes('/cancellation') && resp.request().method() === 'POST',
        { timeout: 15000 }
      ),
      card.getByRole('button', { name: '取消する', exact: true }).click(),
    ]);
  }
);

Then('取消した受注が「対応が要る」群から消える', async ({ page }) => {
  await expect(ownCard(page)).toHaveCount(0, { timeout: 15000 });
});

Then('取消アーカイブに理由 {string} の行が現れる', async ({ page }, reason: string) => {
  await page.getByRole('button', { name: /^取消 \d+ 件$/ }).click();
  // 結末を確かめるために詳細を開かなくて済むよう、行が理由・実行者・時刻を名乗る
  await expect(page.getByText(reason, { exact: false })).toBeVisible({ timeout: 15000 });
});

When('カードから完了モーダルを開き会計 {string} 円で完了する', async ({ page }, fee: string) => {
  await ownCard(page).getByRole('button', { name: '完了', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('会計金額', { exact: true }).fill(fee);
  await Promise.all([
    page.waitForResponse(
      resp => resp.url().includes('/completion') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    dialog.getByRole('button', { name: '完了する', exact: true }).click(),
  ]);
});

Then('完了した受注が「対応が要る」群から消える', async ({ page }) => {
  // 完了の応答は伝票 QR を出すことがあるので、モーダルを閉じてから群を見る
  await page.getByRole('button', { name: /閉じる|キャンセル/ }).first().click({ trial: false }).catch(() => {});
  await expect(ownCard(page)).toHaveCount(0, { timeout: 15000 });
});

Then('完了アーカイブに会計 {string} の行が現れる', async ({ page }, amount: string) => {
  await page.getByRole('button', { name: /^完了 \d+ 件$/ }).click();
  await expect(page.getByText(`会計 ${amount}`, { exact: false })).toBeVisible({ timeout: 15000 });
});

After(async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  if (createdOrderId) {
    // 受注は消せない（ADR 0013）ので、終端へ送って対応が要る群から外すだけ。
    // シナリオ側で既に取消・完了まで進んでいれば撥ねられるが、それは想定内なので握り潰す。
    await cancelOrder(request, token, createdOrderId, 'e2e の後片付け').catch(() => {});
  }
  // 播種したキャストは片付けない。受注が指名として参照したまま残り（受注を消す口は無い。ADR 0013）、
  // fk_t_orders_cast は RESTRICT なので削除は必ず失敗する。失敗すると分かっている呼び出しを
  // 握り潰すと、本当に片付くはずのものが片付かなくなったときにも気づけない。
  createdCastId = '';
  createdCastName = '';
  createdOrderId = '';
  customerName = '';
  storeId = '';
});
