import { expect, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { STORE1_ID, storePath } from './store-api';

const { When, Then } = createBdd();

const loginButton = (page: Page) => page.getByRole('button', { name: 'ログイン', exact: true });

When('未ログインで {string} を開く', async ({ page }, path: string) => {
  await page.context().clearCookies();
  await page.goto(`${APP_URL}${path}`, { waitUntil: 'domcontentloaded' });
});

Then(
  'ログイン画面へ移り、next に {string} が載っている',
  async ({ page }, path: string) => {
    await expect(page).toHaveURL(
      (url) => url.pathname === '/auth/login' && url.searchParams.get('next') === path,
      { timeout: 15000 },
    );
    await expect(loginButton(page)).toBeVisible({ timeout: 15000 });
  },
);

Then('{string} へ戻る', async ({ page }, target: string) => {
  await expect(page).toHaveURL(
    (url) => `${url.pathname}${url.search}${url.hash}` === target,
    { timeout: 15000 },
  );
});

Then('ブラウザに会員の戻り先 cookie が無い', async ({ page }) => {
  const names = (await page.context().cookies()).map((cookie) => cookie.name);
  expect(names).not.toContain('member-return-path');
});

When(
  'next に {string} を載せたログイン画面を開き、メール {string} とパスワード {string} でログインする',
  async ({ page }, next: string, email: string, password: string) => {
    await page.goto(`${APP_URL}/auth/login?next=${encodeURIComponent(next)}`, {
      waitUntil: 'domcontentloaded',
    });
    await page.getByLabel('メールアドレス', { exact: true }).fill(email);
    await page.getByLabel('パスワード', { exact: true }).fill(password);
    await loginButton(page).click();
  },
);

// 店舗の画面のパスは店舗 id を含むので、実行時の id から組む。
When('未ログインで店舗1の {string} を開く', async ({ page }, subPath: string) => {
  await page.context().clearCookies();
  await page.goto(`${APP_URL}${storePath(STORE1_ID, subPath)}`, { waitUntil: 'domcontentloaded' });
});

Then('ログイン画面へ移り、next に店舗1の {string} が載っている', async ({ page }, subPath: string) => {
  const path = storePath(STORE1_ID, subPath);
  await expect(page).toHaveURL(
    (url) => url.pathname === '/auth/login' && url.searchParams.get('next') === path,
    { timeout: 15000 },
  );
  await expect(loginButton(page)).toBeVisible({ timeout: 15000 });
});

Then('店舗1の {string} へ戻る', async ({ page }, subPath: string) => {
  const path = storePath(STORE1_ID, subPath);
  await expect(page).toHaveURL((url) => url.pathname === path, { timeout: 15000 });
});
