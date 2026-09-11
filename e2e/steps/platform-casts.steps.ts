import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { acceptCastInvitation, createCast, issueCastInvitation, loginAsStoreAdmin } from './store-api';

const { Given, When, Then } = createBdd();
let name: string;

Given('照会用キャストの招待を受諾する', async ({ request }) => {
  const suffix = Date.now();
  name = `本人照会E2E-${suffix}`;
  const token = await loginAsStoreAdmin(request);
  const enrollment = await createCast(request, token, name);
  const invitation = await issueCastInvitation(request, token, enrollment);
  await acceptCastInvitation(request, invitation, `person-query-${suffix}@kizuna.test`, 'pass12345', name);
});

When('照会用キャストを検索して本人詳細を開く', async ({ page }) => {
  await page.getByRole('textbox', { name: '表示名または本名' }).fill(name);
  await page.getByRole('button', { name: '検索', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: name });
  await row.getByRole('link', { name: '在籍を確認' }).click();
});

Then('照会用キャストの本人情報と在籍が表示される', async ({ page }) => {
  await expect(page.getByRole('heading', { name: 'キャスト本人情報' })).toBeVisible();
  await expect(page.getByRole('definition').filter({ hasText: name })).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row.getByText('在籍中', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '編集', exact: true })).toHaveCount(0);
});
