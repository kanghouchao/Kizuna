import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { addCustomerContact, createCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore, STORE_HEADERS } from './store-api';
import { PLATFORM_URL } from '../base-url';

const { Given, When, Then } = createBdd();
let survivingId = '';
let mergedId = '';
let storeId = '';
let token = '';
let marker = '';
const reason = '本人の資料を照合し重複を確認';

Given('統合の比較用に長い注意事項と同値連絡先を持つ二顧客がいる', async ({ page, request }) => {
  token = await loginAsStoreAdmin(request);
  marker = `統合確認${Date.now()}`;
  survivingId = await createCustomer(request, token, `${marker}甲`);
  mergedId = await createCustomer(request, token, `${marker}乙`);
  for (const id of [survivingId, mergedId]) {
    await addCustomerContact(request, token, id, 'EMAIL', `${marker}@example.com`);
    const updated = await request.put(`/api/store/customers/${id}`, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { ng_content: '連絡前に在宅状況を確認してください。'.repeat(20) } });
    expect(updated.ok()).toBeTruthy();
  }
  storeId = await loginViaUiAndEnterStore(page);
});
When('顧客一覧の二行から統合資料を選ぶ', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers`);
  await page.getByPlaceholder('名前・電話・メール・LINE ID で検索...').fill(marker);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  for (const suffix of ['甲', '乙']) await page.getByRole('checkbox', { name: `${marker}${suffix} を見比べる` }).click();
  await page.getByRole('radio', { name: `${marker}甲 を残す` }).check();
  await page.getByRole('button', { name: '統合する', exact: true }).click();
  await page.getByRole('button', { name: '被統合側の氏名を採用' }).click();
  await page.getByRole('combobox', { name: 'メールの優先連絡先' }).click();
  await page.getByRole('option', { name: new RegExp(`由来: ${mergedId}`) }).click();
  await expect(page.getByRole('combobox', { name: 'メールの優先連絡先' })).toContainText(`${marker}@example.com`);
  await expect(page.getByRole('combobox', { name: 'ペットの有無' })).toContainText('未確認');
  await page.getByLabel('統合理由').fill(reason);
});
Then('統合資料を狭幅と両テーマでキーボード確認できる', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.evaluate(t => { localStorage.setItem('theme', t); document.documentElement.classList.toggle('dark', t === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      await page.getByRole('heading', { name: '顧客統合の資料と影響を確認' }).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `test-results/customer-merge-${theme}-${width}-sources.png`, fullPage: true });
      await page.getByLabel('統合理由').scrollIntoViewIfNeeded();
      await page.getByLabel('統合理由').focus();
      await page.keyboard.press('Tab');
      await expect(page.getByRole('button', { name: '確定資料でプレビュー' })).toBeFocused();
      const dialog = page.getByRole('dialog');
      expect(await dialog.evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true);
      await page.screenshot({ path: `test-results/customer-merge-${theme}-${width}.png`, fullPage: true });
    }
  }
  await page.getByRole('button', { name: '確定資料でプレビュー' }).click();
  await expect(page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' })).toBeEnabled();
});
When('プレビュー後に原資料が変更される', async ({ request }) => {
  const changed = await request.put(`/api/store/customers/${mergedId}`, { headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` }, data: { ng_content: '新しい注意事項を確認してください' } });
  expect(changed.ok()).toBeTruthy();
});
Then('古いプレビューの実行は拒否され入力を保持する', async ({ page }) => {
  await page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' }).check();
  await page.getByRole('dialog').getByRole('button', { name: '統合する', exact: true }).click();
  await page.getByRole('button', { name: '統合を確定' }).click();
  await expect(page.getByRole('alert')).toContainText('関連情報が変更されました');
  await expect(page.getByLabel('統合理由')).toHaveValue(reason);
  await expect(page.getByRole('dialog').getByRole('button', { name: '統合する', exact: true })).toBeDisabled();
});
When('最新資料を再確認して統合を確定する', async ({ page }) => {
  await page.getByRole('button', { name: '再試行', exact: true }).click();
  await expect(page.getByText('新しい注意事項を確認してください', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '確定資料でプレビュー' }).click();
  await page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' }).check();
  await page.getByRole('dialog').getByRole('button', { name: '統合する', exact: true }).click();
  await page.getByRole('button', { name: '統合を確定' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
Then('統合前後の資料と連絡先の移動を監査で確認できる', async ({ page }) => {
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/${survivingId}/edit`);
  await page.getByRole('button', { name: '監査記録を確認' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(reason, { exact: true })).toBeVisible();
  for (const title of ['存続側の原資料', '被統合側の原資料', '確定資料']) await expect(dialog.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(dialog).toContainText('新しい注意事項を確認してください');
  await expect(dialog).toContainText('移動した連絡先 ID');
  await page.screenshot({ path: 'test-results/customer-merge-audit.png', fullPage: true });
});
