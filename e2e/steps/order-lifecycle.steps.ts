import { expect, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { STORE_HEADERS, createCustomer, registerMember, linkMemberToCustomer, cancelOrder, createCast, createCourse, getOrder, loginAsStoreAdmin, loginViaUiAndEnterStore } from './store-api';

const { Given, When, Then, After } = createBdd();

// 後端の「本日」判定（app.timezone 既定 Asia/Tokyo）に合わせて日付を計算する。
const todayInTokyo = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());

// 播種した実体。受注を消す口は無い（ADR 0013）ので、後片付けは取消で終端へ送るだけ。
let createdCastId = '';
let createdCastName = '';
let createdOrderId = '';
let courseName = '';
let courseId = '';
let courseVersion = 1;
let adoptedPrice = 12000;
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
  courseName = `受注コース-${Date.now()}`;
  courseId = await createCourse(request, token, courseName, storeId);
  courseVersion = 1;
  adoptedPrice = 12000;
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

  await page.getByRole('combobox', { name: 'コース', exact: true }).click();
  await page.getByLabel('コースを検索', { exact: true }).fill(courseName);
  await page.getByRole('option', { name: new RegExp(courseName) }).click();
  await page.getByRole('button', { name: '登録する', exact: true }).click();
  // 受付担当は選ばない。既定の「自分」＝項目ごと省略送信で、サーバが実行者本人に解決する
  const [response] = await Promise.all([
    page.waitForResponse(
      resp =>
        resp.url().endsWith('/api/store/orders') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: 'この内容を確認して保存', exact: true }).click(),
  ]);
  expect(response.status()).toBe(201);
  createdOrderId = (await response.json()).id as string;
});

Then('登録した受注が「対応が要る」群に確定として現れる', async ({ page }) => {
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/?$`), { timeout: 15000 });
  // 電話口で受けると決めた時点で可否は判断済み。画面上でもう一度確定し直す段は無い
  await expect(ownCard(page).getByText('確定', { exact: true })).toBeVisible({ timeout: 15000 });
});

When('受注の編集ページを開き人数を {string} に直して保存する', async ({ page }, pax: string) => {
  await ownCard(page).getByRole('button', { name: '編集', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/${createdOrderId}/edit$`), {
    timeout: 15000,
  });
  // 開くたびに 1 件を読み直すので、播かれるまで待ってから書き換える
  await expect(page.getByLabel('人数', { exact: true })).toHaveValue('2', { timeout: 15000 });
  await page.getByLabel('人数', { exact: true }).fill(pax);
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await Promise.all([
    page.waitForResponse(
      resp =>
        resp.url().includes(`/api/store/orders/${createdOrderId}`) &&
        resp.request().method() === 'PUT',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: 'この内容を確認して保存', exact: true }).click(),
  ]);
  // 保存の出口は一覧。戻り着くまで待たないと、次の段がまだ編集ページを相手にする
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/?$`), { timeout: 15000 });
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
  // 会計金額の欄は無く、合計は明細の総和としてサーバが導出する。1 行だけ入れて総額を作る
  await dialog.getByRole('button', { name: 'クレジット加算を追加' }).click();
  await dialog.getByLabel('明細1の名称', { exact: true }).fill('会計');
  await dialog.getByLabel('明細1の金額', { exact: true }).fill(String(Number(fee) - adoptedPrice));
  await dialog.getByRole('button', { name: '完了する', exact: true }).click();
  await Promise.all([
    page.waitForResponse(
      resp => resp.url().endsWith('/completion') && resp.request().method() === 'POST',
      { timeout: 15000 }
    ),
    page.getByRole('button', { name: 'この内容を確認して保存', exact: true }).click(),
  ]);
});

Then('完了した受注が「対応が要る」群から消える', async ({ page }) => {
  // 完了の応答は伝票 QR を出すことがあるので、モーダルを閉じてから群を見る
  await page.getByRole('button', { name: /閉じる|キャンセル/ }).first().click({ trial: false }).catch(() => {});
  await expect(ownCard(page)).toHaveCount(0, { timeout: 15000 });
});

Then('完了アーカイブに請求 {string} の行が現れる', async ({ page }, amount: string) => {
  await page.getByRole('button', { name: /^完了 \d+ 件$/ }).click();
  await expect(page.getByText(`請求 ${amount}`, { exact: false })).toBeVisible({ timeout: 15000 });
});

When('設定コースを改定して受注へ適用する', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const updated = await request.put(`/api/store/services/${courseId}`, {
    headers: { ...STORE_HEADERS, 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    data: { name: courseName, duration_minutes: 90, price: 18000, remuneration: 11000, expected_version: courseVersion },
  });
  expect(updated.status()).toBe(200);
  courseVersion = 2;
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders/${createdOrderId}/edit`);
  await page.getByRole('combobox', { name: 'コース', exact: true }).click();
  await page.getByLabel('コースを検索').fill(courseName);
  await page.getByRole('option', { name: new RegExp(courseName) }).click();
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByText(/固定報酬: ¥11,000/)).toBeVisible();
  await page.getByRole('button', { name: 'この内容を確認して保存' }).click();
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/?$`));
  adoptedPrice = 18000;
});

When('設定を削除して過去のコースへ理由付きで訂正する', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const deleted = await request.delete(`/api/store/services/${courseId}?expected_version=${courseVersion}`, {
    headers: { ...STORE_HEADERS, 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
  });
  expect(deleted.status()).toBe(204);
  courseId = '';
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders/${createdOrderId}/correction`);
  await page.getByRole('combobox', { name: '訂正する過去の版' }).click();
  await page.getByLabel('コースを検索').fill(courseName);
  await page.getByRole('option', { name: new RegExp(`${courseName}.*版1.*削除済み`) }).click();
  await page.getByLabel('理由', { exact: true }).fill('実際に提供した60分コースへ訂正');
  await page.getByRole('button', { name: '訂正する' }).click();
  await expect(page.getByText(/固定報酬: ¥7,000/)).toBeVisible();
  const [saved] = await Promise.all([
    page.waitForResponse(response => response.url().endsWith('/corrections') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'この内容を確認して保存' }).click(),
  ]);
  expect(saved.status()).toBe(201);
  const result = await saved.json();
  expect(result.previous_course.price).toBe(18000);
  expect(result.course.price).toBe(12000);
  expect(result.course.remuneration).toBe(7000);
  expect(result.total_fee).toBe(22000);
  expect(result.correction_id).toBeTruthy();
  await expect(page.getByRole('heading', { name: '訂正しました', exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/935-correction-result.png', animations: 'disabled', fullPage: true });
});

Then('店舗から同じ訂正の費用と報酬を照会できる', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders/${createdOrderId}/edit`);
  await page.getByRole('button', { name: '訂正履歴', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '訂正履歴', exact: true });
  await expect(dialog.getByText('実際に提供した60分コースへ訂正')).toBeVisible();
  await expect(dialog.getByText('請求 ¥28,000 → ¥22,000', { exact: true })).toBeVisible();
  await expect(dialog.getByText('発生済み報酬 ¥11,000 → ¥7,000', { exact: true })).toBeVisible();
  await expect(dialog.getByText(/原営業日/)).toBeVisible();
  await expect(dialog.getByText(/訂正日時/)).toBeVisible();
  await page.emulateMedia({ colorScheme: 'light' });
  await page.screenshot({ path: 'test-results/935-store-history-light.png', animations: 'disabled' });
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.screenshot({ path: 'test-results/935-store-history-dark.png', animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(dialog).toBeVisible();
  await page.screenshot({ path: 'test-results/935-store-history-narrow.png', animations: 'disabled' });
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('button', { name: '訂正履歴', exact: true })).toBeFocused();
  await page.setViewportSize({ width: 1280, height: 720 });
  await page.emulateMedia({ colorScheme: 'light' });
  await page.context().clearCookies();
});

Then('平台から同じ訂正の費用と報酬を照会できる', async ({ page }) => {
  await expect(page).toHaveURL(/\/platform\/dashboard/);
  await page.goto(`${PLATFORM_URL}/platform/orders`);
  const row = page.getByRole('listitem').filter({ hasText: `受注 ${createdOrderId}` });
  await expect(row.getByText('請求総額 ¥22,000', { exact: true })).toBeVisible();
  await page.screenshot({ path: 'test-results/935-platform-list.png', animations: 'disabled' });
  await row.getByRole('button', { name: '訂正履歴', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '訂正履歴', exact: true });
  await expect(dialog.getByText('請求 ¥28,000 → ¥22,000', { exact: true })).toBeVisible();
  await expect(dialog.getByText('発生済み報酬 ¥11,000 → ¥7,000', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: '訂正する', exact: true })).toHaveCount(0);
  await page.screenshot({ path: 'test-results/935-platform-history.png', animations: 'disabled' });
});

After(async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  if (courseId) {
    const deleted = await request.delete(`/api/store/services/${courseId}?expected_version=${courseVersion}`, {
      headers: { ...STORE_HEADERS, 'X-Store-ID': storeId, Authorization: `Bearer ${token}` },
    });
    expect(deleted.status()).toBe(204);
    courseId = '';
  }
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

When('有料と無料の延長・設定加算・固定割引を追加する', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const surchargeName = `受注加算-${Date.now()}`;
  const response = await request.post(`${PLATFORM_URL}/api/store/services`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    data: { kind: 'SURCHARGE', name: surchargeName, price: 1000, remuneration: 500 },
  });
  expect(response.status()).toBe(201);
  await ownCard(page).getByRole('button', { name: '編集', exact: true }).click();
  await page.getByRole('button', { name: '延長を追加', exact: true }).click();
  await page.getByLabel('明細1の分数', { exact: true }).fill('30');
  await page.getByLabel('明細1の金額', { exact: true }).fill('3000');
  await page.getByLabel('明細1の固定報酬', { exact: true }).fill('2000');
  await page.getByRole('button', { name: '延長を追加', exact: true }).click();
  await page.getByLabel('明細2の名称', { exact: true }).fill('無料延長');
  await page.getByLabel('明細2の分数', { exact: true }).fill('15');
  await page.getByRole('button', { name: '加算を選択', exact: true }).click();
  await page.getByLabel('加算を検索', { exact: true }).fill(surchargeName);
  await page.getByRole('option', { name: new RegExp(surchargeName) }).click();
  await page.getByRole('button', { name: '割引を追加', exact: true }).click();
  await page.getByLabel('明細4の名称', { exact: true }).fill('優待');
  await page.getByLabel('明細4の金額', { exact: true }).fill('15000');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(/総時間: 105分/)).toBeVisible();
  await expect(dialog.getByText('請求額: ¥1,000', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'この内容を確認して保存', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/store/${storeId}/orders/?$`));
});

Then('再表示した受注に各回の延長と加算の採用条件が残る', async ({ page }) => {
  await ownCard(page).getByRole('button', { name: '編集', exact: true }).click();
  await expect(page.getByLabel('明細1の分数', { exact: true })).toHaveValue('30');
  await expect(page.getByLabel('明細2の分数', { exact: true })).toHaveValue('15');
  await expect(page.getByLabel('明細2の固定報酬', { exact: true })).toHaveValue('0');
  await expect(page.getByText(/受注加算-.*料金 ¥1,000.*固定報酬 ¥500.*版1/)).toBeVisible();
  await expect(page.getByLabel('明細4の金額', { exact: true })).toHaveValue('15000');
});

Then(
  "完了アーカイブに発生済み報酬と独立した完了日時が現れる",
  async ({ page, request }) => {
    const row = page
      .locator("div.min-w-0")
      .filter({ hasText: customerName })
      .filter({ hasText: "発生済み報酬" });
    await expect(
      row.getByText("発生済み報酬: ¥7,000", { exact: true }),
    ).toBeVisible();
    const token = await loginAsStoreAdmin(request);
    const completedAt = (await getOrder(request, token, storeId, createdOrderId)).completed_at;
    expect(completedAt).toBeTruthy();
    const localTime = await page.evaluate(value => new Date(value).toLocaleString('ja-JP'), completedAt!);
    await expect(row.getByText(`完了日時: ${localTime}`, { exact: true })).toBeVisible();
    await expect(row.getByText("支払済み", { exact: false })).toHaveCount(0);
  },
);

Given('3000ポイントを利用した請求7000円の完了受注がある', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const headers = { ...STORE_HEADERS, 'X-Store-ID': storeId, Authorization: `Bearer ${token}` };
  const memberCode = await registerMember(request, `rollback-${Date.now()}@example.test`, crypto.randomUUID(), customerName);
  const customerId = await createCustomer(request, token, customerName, `09${Date.now().toString().slice(-9)}`);
  await linkMemberToCustomer(request, token, customerId, memberCode);
  const adjustment = await request.post(`/api/store/customers/${customerId}/point-adjustments`, {
    headers, data: { delta: 3000, reason: '利用取消の原資', idempotency_key: crypto.randomUUID() },
  });
  expect(adjustment.status()).toBe(200);
  const input = { customer_id: customerId, cast_id: createdCastId, course_id: courseId,
    business_date: todayInTokyo(), pax: 1, fee_lines: [{ kind: 'DISCOUNT', name: '割引', amount: 2000 }] };
  const preview = await request.post('/api/store/orders/preview', { headers, data: input });
  expect(preview.status()).toBe(200);
  const created = await request.post('/api/store/orders', { headers, data: { ...input, confirmation_token: (await preview.json()).confirmation_token } });
  expect(created.status()).toBe(201);
  createdOrderId = (await created.json()).id;
  const order = await getOrder(request, token, storeId, createdOrderId);
  const completion = { expected_version: order.version, fee_lines: [{ kind: 'DISCOUNT', name: '割引', amount: 2000 }], use_points: 3000 };
  const completionPreview = await request.post(`/api/store/orders/${createdOrderId}/completion-preview`, { headers, data: completion });
  expect(completionPreview.status()).toBe(200);
  const completed = await request.post(`/api/store/orders/${createdOrderId}/completion`, { headers, data: { ...completion, confirmation_token: (await completionPreview.json()).confirmation_token } });
  expect(completed.status()).toBe(200);
});

When('専用入口で理由 {string} を確認して巻き戻す', async ({ page }, reason: string) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/orders`);
  await page.getByRole('button', { name: /^完了 \d+ 件$/ }).click();
  await page.locator(`a[href$="/${createdOrderId}/point-rollback"]`).click();
  await expect(page.getByText('7,000 円', { exact: true })).toBeVisible();
  await expect(page.getByText('10,000 円', { exact: true })).toBeVisible();
  await page.getByLabel('理由', { exact: true }).fill(reason);
  await page.getByRole('button', { name: '巻き戻す', exact: true }).click();
  await page.getByRole('button', { name: '実行する', exact: true }).click();
  await expect(page.getByRole('heading', { name: '巻き戻しました' })).toBeVisible();
});

Then('請求10000円と元利用と相殺が残り再訪しても処置履歴が見える', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const order = await getOrder(request, token, storeId, createdOrderId);
  expect(order.total_fee).toBe(10000);
  expect(order.fee_lines).toEqual(expect.arrayContaining([
    expect.objectContaining({ kind: 'POINT_REDEMPTION', amount: 3000, system_owned: true }),
    expect.objectContaining({ kind: 'POINT_REDEMPTION_OFFSET', amount: 3000, system_owned: true }),
  ]));
  await page.reload();
  await expect(page.getByText('理由：利用取消の救済')).toBeVisible();
  await expect(page.getByText('10,000 円', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '巻き戻す', exact: true })).toHaveCount(0);
});

When('未提供の誤完了を確認して無効化する', async ({ page }) => {
  await page.getByRole('link', { name: '誤完了の無効化へ', exact: true }).click();
  await expect(page.getByText(/有効な請求 10,000 円 → 0 円/)).toBeVisible();
  await page.getByLabel('理由', { exact: true }).fill('全く提供していない誤完了');
  await page.getByRole('button', { name: '無効化を確認', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('button', { name: '無効化を確認', exact: true })).toBeFocused();
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: '未提供を確認して無効化', exact: true }).click();
  await expect(page.getByText(/変更 ID：/)).toBeVisible();
});

Then('原記録と零の請求報酬が残り新受注へ再提供を記録できる', async ({ page, request }) => {
  const token = await loginAsStoreAdmin(request);
  const original = await getOrder(request, token, storeId, createdOrderId);
  expect(original.total_fee).toBe(0);
  expect(original.accrued_remuneration).toBe(0);
  await expect(page.getByText(/原報酬/).first()).toBeVisible();
  await page.screenshot({ path: 'test-results/937-invalidation-light.png', fullPage: true });
  await page.evaluate(() => document.documentElement.classList.add('dark'));
  await page.screenshot({ path: 'test-results/937-invalidation-dark.png', fullPage: true });
  await page.evaluate(() => document.documentElement.classList.remove('dark'));
  await page.setViewportSize({ width: 390, height: 844 });
  const shell = page.locator('div.overflow-x-auto').filter({ has: page.locator('main') });
  await shell.evaluate(element => { element.scrollLeft = element.scrollWidth; });
  expect(await shell.evaluate(element => element.scrollLeft)).toBeGreaterThan(0);
  await page.screenshot({ path: 'test-results/937-invalidation-narrow-right.png', fullPage: true });
  await page.getByRole('link', { name: '関連する新受注で再提供', exact: true }).focus();
  await expect(page.getByRole('link', { name: '関連する新受注で再提供', exact: true })).toBeInViewport();
  await page.screenshot({ path: 'test-results/937-invalidation-narrow.png', fullPage: true });
  await page.keyboard.press('Enter');
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(page.getByText(/再提供の元受注：/)).toBeVisible();
  await page.getByLabel('お客様名', { exact: true }).fill(customerName + '再提供');
  await page.getByLabel('営業日', { exact: true }).fill(todayInTokyo());
  await page.getByLabel('キャスト *', { exact: true }).click();
  await page.getByPlaceholder('名前で検索').fill(createdCastName);
  await page.getByRole('option', { name: createdCastName }).click();
  await page.getByRole('combobox', { name: 'コース', exact: true }).click();
  await page.getByLabel('コースを検索', { exact: true }).fill(courseName);
  await page.getByRole('option', { name: new RegExp(courseName) }).click();
  await page.getByRole('button', { name: '登録する', exact: true }).click();
  const [saved] = await Promise.all([
    page.waitForResponse(r => r.url().endsWith('/api/store/orders') && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'この内容を確認して保存', exact: true }).click(),
  ]);
  expect(saved.status()).toBe(201);
  const replacement = await saved.json();
  expect(replacement.replacement_for_order_id).toBe(createdOrderId);
  expect(replacement.completion_invalidated).toBe(false);
  expect(replacement.status).toBe('CONFIRMED');
  expect(replacement.total_fee).toBe(12000);
  await cancelOrder(request, token, replacement.id, '検証終了');
});
