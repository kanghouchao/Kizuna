import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { addCustomerContact, preferCustomerContact, createCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore, updateCustomer, storeUrl, STORE1_ID } from './store-api';

const { Given, When, Then } = createBdd();
let survivingId = '';
let mergedId = '';
let token = '';
let marker = '';
const reason = '本人の資料を照合し重複を確認';
const longName = '長谷川左衛門三郎太'.repeat(6);

Given('統合の比較用に長い注意事項と同値連絡先を持つ二顧客がいる', async ({ page, request }) => {
  token = await loginAsStoreAdmin(request);
  marker = `統合確認${Date.now()}`;
  // 長い人名でも比較と採用の欄が崩れないことを見るため、名前を長くする
  survivingId = await createCustomer(request, token, `${marker}甲${longName}`);
  mergedId = await createCustomer(request, token, `${marker}乙${longName}`);
  for (const id of [survivingId, mergedId]) {
    const contactId = await addCustomerContact(request, token, id, 'EMAIL', `${marker}@example.com`);
    await preferCustomerContact(request, token, id, 'EMAIL', contactId);
    await updateCustomer(request, token, id, { ng_content: '連絡前に在宅状況を確認してください。'.repeat(20) });
  }
  await loginViaUiAndEnterStore(page);
});
When('顧客一覧の二行から統合資料を選ぶ', async ({ page }) => {
  await page.goto(storeUrl(STORE1_ID, `/customers`));
  await page.getByPlaceholder('名前・電話・メール・LINE ID で検索...').fill(marker);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  for (const suffix of ['甲', '乙']) await page.getByRole('checkbox', { name: `${marker}${suffix}${longName} を見比べる` }).click();
  await page.getByRole('radio', { name: `${marker}甲${longName} を残す` }).check();
  await page.getByRole('link', { name: '統合する', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/store/${STORE1_ID}/customers/merge\\?surviving=${survivingId}&merged=${mergedId}$`));
  await expect(page.getByRole('heading', { level: 1, name: '顧客の統合' })).toBeVisible();
  await page.getByRole('button', { name: '被統合側の氏名を採用' }).click();
  await page.getByLabel('統合理由').fill(reason);
  await page.getByRole('button', { name: '確定資料でプレビュー' }).click();
  const preference = page.getByRole('combobox', { name: 'メールの優先連絡先' });
  await expect(preference).toBeFocused();
  await expect(preference).toHaveAttribute('aria-invalid', 'true');
  await expect(preference).toHaveAccessibleDescription('優先連絡先を一件、または指定なしを選択してください');
  await preference.click();
  await page.getByRole('option', { name: new RegExp(`由来: ${mergedId}`) }).click();
  await expect(page.getByRole('combobox', { name: 'メールの優先連絡先' })).toContainText(`${marker}@example.com`);
  await expect(page.getByRole('combobox', { name: 'ペットの有無' })).toContainText('未確認');
  await page.getByLabel('統合理由').fill(reason);
});
Then('統合資料を狭幅と両テーマでキーボード確認できる', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.evaluate(t => { localStorage.setItem('theme', t); document.documentElement.classList.toggle('dark', t === 'dark'); }, theme);
    for (const width of [390, 1024, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      await page.getByRole('heading', { name: '統合する 2 件' }).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `test-results/customer-merge-${theme}-${width}-sources.png`, fullPage: true });
      await page.getByRole('heading', { name: '統合後の資料', exact: true }).scrollIntoViewIfNeeded();
      await page.screenshot({ path: `test-results/customer-merge-${theme}-${width}-adopt.png`, fullPage: true });
      // 1024px 以上では内容列が収まるので、画面全体も横に溢れない
      if (width >= 1024) expect(await page.locator('main').evaluate(node => node.scrollWidth <= node.clientWidth + 1)).toBe(true);
      await page.getByLabel('統合理由').scrollIntoViewIfNeeded();
      await page.getByLabel('統合理由').focus();
      await page.keyboard.press('Tab');
      await expect(page.getByRole('button', { name: '確定資料でプレビュー' })).toBeFocused();
      // 狭幅で管理画面の内容列が横スクロールになるのは既定の作りなので、各節の中で溢れないことを確かめる
      expect(await page.locator('main section').evaluateAll(nodes => nodes.every(node => node.scrollWidth <= node.clientWidth + 1))).toBe(true);
      await page.screenshot({ path: `test-results/customer-merge-${theme}-${width}.png`, fullPage: true });
    }
  }
  await page.getByRole('button', { name: '確定資料でプレビュー' }).click();
  await expect(page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' })).toBeEnabled();
});
When('プレビュー後に原資料が変更される', async ({ request }) => {
  await updateCustomer(request, token, mergedId, { ng_content: '新しい注意事項を確認してください' });
});
Then('古いプレビューの実行は拒否され入力を保持する', async ({ page }) => {
  await page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' }).check();
  await page.getByRole('button', { name: '統合する', exact: true }).click();
  await page.getByRole('button', { name: '統合を確定' }).click();
  // 画面遷移で Next の経路告知（role=alert）も並ぶので、文言で絞る
  await expect(page.getByRole('alert').filter({ hasText: '関連情報が変更されました' })).toBeVisible();
  await expect(page.getByLabel('統合理由')).toHaveValue(reason);
  await expect(page.getByRole('button', { name: '統合する', exact: true })).toBeDisabled();
});
When('最新資料を再確認して統合を確定する', async ({ page }) => {
  await page.getByRole('button', { name: '再試行', exact: true }).click();
  const mergedSource = page.locator('section').filter({ has: page.getByRole('heading', { name: '被統合側の原資料', exact: true }) });
  await expect(mergedSource.getByText('新しい注意事項を確認してください', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '確定資料でプレビュー' }).click();
  await page.getByRole('checkbox', { name: '双方の注意事項・確定資料・優先指定・会員への影響を確認しました' }).check();
  await page.getByRole('button', { name: '統合する', exact: true }).click();
  await page.getByRole('button', { name: '統合を確定' }).click();
  await expect(page).toHaveURL(/\/store\/\d+\/customers$/);
});
Then('統合前後の資料と連絡先の移動を監査で確認できる', async ({ page }) => {
  await page.goto(storeUrl(STORE1_ID, `/customers/${survivingId}/edit`));
  await page.getByRole('button', { name: '監査記録を確認' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(reason, { exact: true })).toBeVisible();
  for (const title of ['存続側の原資料', '被統合側の原資料', '確定資料']) await expect(dialog.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(dialog).toContainText('新しい注意事項を確認してください');
  await expect(dialog).toContainText('移動した連絡先 ID');
  await page.screenshot({ path: 'test-results/customer-merge-audit.png', fullPage: true });
});
