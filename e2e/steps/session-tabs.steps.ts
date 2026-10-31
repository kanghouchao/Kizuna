import { expect, type Page } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { APP_URL } from '../base-url';
import { changeOwnPassword, loginInBrowser, registerMember, revealSidebarLink } from './store-api';

const { Given, When, Then } = createBdd();

const LOGIN_URL = `${APP_URL}/auth/login`;
const MEMBER_PASSWORD = 'pass12345';

// 別のタブ（同じブラウザ文脈の 2 枚目のページ）。シナリオごとに作り直す。
let otherTab: Page | null = null;
// このシナリオで自助登録した会員の電子メール。
let memberEmail = '';

const loginForm = (page: Page) => page.getByRole('button', { name: 'ログイン', exact: true });

Then(
  'ブラウザの cookie に {string} が無く、リフレッシュ cookie は JS から読めない',
  async ({ page }, name: string) => {
    // URL で絞ると Path=/api/auth のリフレッシュ cookie が落ちるので、文脈の全 cookie を見る。
    const cookies = await page.context().cookies();
    expect(cookies.map(c => c.name)).not.toContain(name);
    expect(cookies.find(c => c.name === 'refresh_token')?.httpOnly).toBe(true);
    expect(await page.evaluate(() => document.cookie)).not.toContain('refresh_token');
  }
);

When('画面を再読込する', async ({ page }) => {
  await page.reload();
});

Then('ヘッダーに {string} が表示される', async ({ page }, email: string) => {
  await expect(page.getByRole('banner').getByText(email, { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

When('ログイン画面を開き直す', async ({ page }) => {
  // フォームが一瞬でも描かれたかを、読み込みの最初から見張る。着地は根 layout を跨ぐ文書の読み直しで window の値が
  // 消えるので、同じタブの間は残る sessionStorage に記録する。
  await page.addInitScript(() => {
    new MutationObserver(() => {
      if (document.getElementById('email')) sessionStorage.setItem('e2e-login-form-seen', '1');
    }).observe(document, { childList: true, subtree: true });
  });
  await page.goto(LOGIN_URL);
});

Then('ログインフォームは一度も表示されていない', async ({ page }) => {
  expect(await page.evaluate(() => sessionStorage.getItem('e2e-login-form-seen'))).toBeNull();
});

When('ブラウザの cookie をすべて消す', async ({ page }) => {
  // 着地直後の画面が投げたリフレッシュの応答が cookie を書き戻さないよう、空のページへ移って在途の要求を打ち切る。
  await page.goto('about:blank');
  await page.context().clearCookies();
});

Then('ログインフォームが表示される', async ({ page }) => {
  await expect(page).toHaveURL(/\/auth\/login/, { timeout: 15000 });
  await expect(loginForm(page)).toBeVisible({ timeout: 15000 });
});

When(
  '同じブラウザの別の経路でメール {string} とパスワード {string} でログインする',
  async ({ page }, email: string, password: string) => {
    // ログイン画面はログイン済みなら着地先へ移るので、別アカウントのリフレッシュ cookie は同じ
    // ブラウザ文脈の要求（cookie を共有する）で直接もらう。会員登録や LINE からのログインと同じ状況になる。
    await loginInBrowser(page, email, password);
  }
);

Then(
  '元のタブでサイドバーの {string} を開くと、要求は {string} として送られて成功する',
  async ({ page, request }, linkName: string, email: string) => {
    const link = await revealSidebarLink(page, aside =>
      aside.getByRole('link', { name: linkName, exact: true })
    );
    const responsePromise = page.waitForResponse(r => r.url().includes('/api/'));
    await link.click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    // 要求が名乗った本人を、同じ Bearer で /me を引いて確かめる（フロントは JWT を解読しないので外から見る）。
    const authorization = (await response.request().allHeaders()).authorization;
    const me = await request.get(`${APP_URL}/api/me`, { headers: { Authorization: authorization } });
    expect((await me.json()).email).toBe(email);
  }
);

When('元のタブで {string} を開くと、その要求でアクセストークンが期限切れになる', async ({ page }, linkName: string) => {
  // 15 分を待たずに期限切れを再現する。リフレッシュ自身は本物のバックエンドへ届く。
  await page.route(
    url => url.pathname.startsWith('/api/') && url.pathname !== '/api/auth/refresh',
    route => route.fulfill({ status: 401, body: '' }),
    { times: 1 }
  );
  const link = await revealSidebarLink(page, aside =>
    aside.getByRole('link', { name: linkName, exact: true })
  );
  const refresh = page.waitForResponse(r => r.url().endsWith('/api/auth/refresh'));
  await link.click();
  // 元のタブは自分の利用者 ID を名乗り、cookie は別のアカウントのものなので 409 になる。
  expect((await refresh).status()).toBe(409);
});

Then(
  'ダイアログ {string} が出て、ヘッダーは {string} のまま',
  async ({ page }, title: string, email: string) => {
    const dialog = page.getByRole('alertdialog');
    await expect(dialog.getByText(title, { exact: true })).toBeVisible({ timeout: 15000 });
    // 自動で再読込すると入力中の内容を黙って捨てる。画面の本人はまだ元のアカウントのまま。
    // モーダルの背後は aria-hidden になり banner の役割では引けないので、要素で引く。
    await expect(page.locator('header').getByText(email, { exact: true })).toBeVisible();
  }
);

When('ダイアログの {string} を押す', async ({ page }, name: string) => {
  await page.getByRole('alertdialog').getByRole('button', { name, exact: true }).click();
});

When('別のタブでダッシュボードを開くと {string} として表示される', async ({ page }, email: string) => {
  otherTab = await page.context().newPage();
  await otherTab.goto(`${APP_URL}/admin/dashboard/`);
  await expect(otherTab.getByRole('banner').getByText(email, { exact: true })).toBeVisible({
    timeout: 15000,
  });
});

When('元のタブでログアウトする', async ({ page }) => {
  await page.bringToFront();
  await page.getByRole('button', { name: 'アカウントメニュー' }).click();
  await page.getByRole('menuitem', { name: 'ログアウト' }).click();
  await expect(page).toHaveURL(/\/auth\/login/, { timeout: 15000 });
});

Then('別のタブがログイン画面へ移る', async () => {
  await expect(otherTab!).toHaveURL(/\/auth\/login/, { timeout: 15000 });
  await otherTab!.close();
  otherTab = null;
});

Given('新しい会員を API で登録する', async ({ request }) => {
  memberEmail = `session-${Date.now()}@kizuna.test`;
  await registerMember(request, memberEmail, MEMBER_PASSWORD, 'セッション検証会員');
});

When('その会員でログインする', async ({ page }) => {
  await page.getByLabel('メールアドレス', { exact: true }).fill(memberEmail);
  await page.getByLabel('パスワード', { exact: true }).fill(MEMBER_PASSWORD);
  await loginForm(page).click();
});

Then('会員ポータルへ遷移する', async ({ page }) => {
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
  await expect(page.getByRole('link', { name: 'ポイントを見る' })).toBeVisible({ timeout: 15000 });
});

When('その会員のパスワードを API で変更する', async ({ request }) => {
  // 資格情報の版が増え、画面が持つアクセストークンもリフレッシュ cookie も失効する。
  await changeOwnPassword(request, memberEmail, MEMBER_PASSWORD, `${MEMBER_PASSWORD}-changed`);
});

When('会員ポータルで {string} を開く', async ({ page }, name: string) => {
  await page.getByRole('link', { name, exact: true }).click();
});

Then(
  '{string} が表示され、ログインへは移らない',
  async ({ page }, message: string) => {
    await expect(page.getByRole('alertdialog').getByText(message, { exact: true })).toBeVisible({
      timeout: 15000,
    });
    await expect(page).toHaveURL(/\/member\//);
  }
);
