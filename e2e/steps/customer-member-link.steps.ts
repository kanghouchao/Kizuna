import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { PLATFORM_URL } from '../base-url';
import { createCustomer, loginAsStoreAdmin, loginViaUiAndEnterStore, registerMember, currentCustomerMemberLink, customerMemberLinkHistory, linkMemberToCustomer } from './store-api';

const { Given, When, Then } = createBdd();
let customerId = '';
let token = '';
let firstCode = '';
let secondCode = '';
const changeReason = '本人確認の結果、提示された会員コードへ変更。'.repeat(12);

Given('会員関連の検証用顧客と二人の会員を用意する', async ({ page, request }) => {
  token = await loginAsStoreAdmin(request);
  customerId = await createCustomer(request, token, `関連理由検証-${Date.now()}`);
  const password = `E2e-${Date.now()}-member`;
  firstCode = await registerMember(request, `link-first-${Date.now()}@kizuna.test`, password, '関連検証一');
  secondCode = await registerMember(request, `link-second-${Date.now()}@kizuna.test`, password, '関連検証二');
  const storeId = await loginViaUiAndEnterStore(page);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/customers/${customerId}/edit`);
  await expect(page.getByText('未紐づけ', { exact: true })).toBeVisible();
});

When('会員コードで関連を成立させ理由付きで別会員に変更する', async ({ page }) => {
  await page.getByLabel('会員コード', { exact: true }).fill(firstCode);
  await page.getByRole('button', { name: '紐づける', exact: true }).click();
  await expect(page.getByRole('button', { name: '変更する', exact: true })).toBeEnabled();
  await page.getByLabel('会員コード', { exact: true }).fill(secondCode);
  await page.getByRole('button', { name: '変更する', exact: true }).click();
  await expect(page.getByText('操作理由を入力してください', { exact: true })).toBeVisible();
  await page.getByLabel('操作理由', { exact: true }).fill(changeReason);
  await page.getByRole('button', { name: '変更する', exact: true }).click();
  const dialog = page.getByRole('alertdialog', { name: '会員の関連を変更しますか？' });
  await expect(dialog).toContainText(firstCode);
  await expect(dialog).toContainText(secondCode);
  await dialog.getByRole('button', { name: '変更する', exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('cell', { name: `解除理由: ${changeReason}`, exact: false })).toBeVisible();
});

Then('関連の区間と理由を狭幅と両テーマで確認できる', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.evaluate(value => { localStorage.setItem('theme', value); document.documentElement.classList.toggle('dark', value === 'dark'); }, theme);
    for (const width of [390, 1280]) {
      await page.setViewportSize({ width, height: 900 });
      await page.getByLabel('操作理由', { exact: true }).fill('本人依頼');
      const release = page.getByRole('button', { name: '解除', exact: true });
      await release.scrollIntoViewIfNeeded();
      await release.focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('alertdialog', { name: '会員の紐づけを解除しますか？' })).toContainText(secondCode);
      await page.keyboard.press('Escape');
      await expect(page.getByRole('alertdialog', { name: '会員の紐づけを解除しますか？' })).toBeHidden();
      await expect(release).toBeInViewport();
      await page.screenshot({ path: `test-results/member-link-${theme}-${width}.png`, fullPage: true });
      if (width === 390) {
        const consoleViewport = page.locator('div.overflow-x-auto').first();
        await consoleViewport.evaluate(element => { element.scrollLeft = element.scrollWidth; });
        await page.screenshot({ path: `test-results/member-link-${theme}-390-right.png` });
        await release.scrollIntoViewIfNeeded();
        await expect(release).toBeInViewport();
      }
    }
  }
});

When('古い画面で解除して競合を確認し再確認して解除する', async ({ page, request }) => {
  const current = await currentCustomerMemberLink(request, token, customerId);
  await linkMemberToCustomer(request, token, customerId, firstCode, { expected_link_id: current.id, operation_reason: '別担当者が再確認' });
  await page.getByLabel('操作理由', { exact: true }).fill('本人から解除依頼');
  await page.getByRole('button', { name: '解除', exact: true }).click();
  await page.getByRole('alertdialog', { name: '会員の紐づけを解除しますか？' }).getByRole('button', { name: '解除する', exact: true }).click();
  await expect(page.getByText('関連状態が変わりました。最新の状態と理由を確認して、もう一度操作してください。')).toBeVisible();
  await expect(page.getByLabel('操作理由', { exact: true })).toHaveValue('本人から解除依頼');
  await page.screenshot({ path: 'test-results/member-link-conflict.png', fullPage: true });
  await page.getByRole('button', { name: '解除', exact: true }).click();
  await expect(page.getByRole('alertdialog', { name: '会員の紐づけを解除しますか？' })).toContainText(firstCode);
  await page.getByRole('alertdialog', { name: '会員の紐づけを解除しますか？' }).getByRole('button', { name: '解除する', exact: true }).click();
  await expect(page.getByText('未紐づけ', { exact: true })).toBeVisible();
});

Then('解除理由を残して最初の会員と再関連できる', async ({ page, request }) => {
  await expect(page.getByText('解除理由: 本人から解除依頼', { exact: true })).toBeVisible();
  await page.getByLabel('会員コード', { exact: true }).fill(firstCode);
  await page.getByRole('button', { name: '再関連する', exact: true }).click();
  await expect(page.getByRole('button', { name: '変更する', exact: true })).toBeEnabled();
  const { content } = await customerMemberLinkHistory(request, token, customerId);
  expect(content).toHaveLength(4);
  expect(content[0].status).toBe('ACTIVE');
  expect(content[1].release_reason).toBe('本人から解除依頼');
  expect(content[3].release_reason).toBe(changeReason);
});
