import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  ADMIN_PASSWORD,
  STORE_HEADERS,
  createPermissionRole,
  createStaffFixture,
  loginUser,
  uploadStoreImage,
} from './store-api';

const { Given, When, Then, After } = createBdd();
let token = '';
let articleId = '';
let version = 0;
let title = '';
let suffix = '';
let coverUrl = '';
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${token}` });
// 記法らしき文字列と改行を含め、HTML や記法として解釈されず書いたとおりに出ることを確かめる
const body = () => `${title} の一行目\n<b>太字ではない</b>\n**強調でもない**`;

async function publicTitles(request: APIRequestContext): Promise<string[]> {
  const response = await request.get('/api/articles/public', { headers: STORE_HEADERS, params: { size: 100 } });
  expect(response.status()).toBe(200);
  return (await response.json()).content.map((a: { title: string }) => a.title);
}

async function setPublication(request: APIRequestContext, data: Record<string, unknown>) {
  const response = await request.put(`/api/articles/${articleId}/publication`, { headers: headers(), data: { version, ...data } });
  expect(response.status(), await response.text()).toBe(200);
  const article = await response.json();
  version = article.version;
  return article;
}

Given('記事を作成している', async ({ request }) => {
  const admin = await loginUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  suffix = Date.now().toString();
  const email = `article-public-${suffix}@kizuna.test`;
  const password = `Article-${suffix}-fixture`;
  const role = await createPermissionRole(request, admin, `記事掲出-${suffix}`, ['ARTICLE_MANAGE']);
  await createStaffFixture(request, admin, email, password, [role]);
  token = await loginUser(request, email, password);
  title = `掲出検証-${suffix}`;
  const created = await request.post('/api/articles', { headers: headers(), data: { title, body: body() } });
  expect(created.status(), await created.text()).toBe(201);
  articleId = (await created.json()).id;
  version = 0;
  // 作成直後は非公開で、公式サイトに載らない
  expect(await publicTitles(request)).not.toContain(title);
});

When('記事を公開に設定する', async ({ request }) => {
  const article = await setPublication(request, { published: true, publish_from: new Date(Date.now() - 60_000).toISOString() });
  expect(article.displayed).toBe(true);
});

Then('お知らせ一覧に記事の表題が表示される', async ({ page }) => {
  await page.goto('/news');
  await expect(page.getByRole('heading', { name: 'お知らせ', exact: true })).toBeVisible();
  await expect(page.getByRole('link').filter({ hasText: title })).toBeVisible();
});

Then('記事ページに本文が文字のまま改行を保って表示される', async ({ page }) => {
  await page.getByRole('link').filter({ hasText: title }).click();
  await expect(page).toHaveURL(new RegExp(`/news/${articleId}$`));
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  const text = page.getByText(`${title} の一行目`);
  expect(await text.innerText()).toBe(body());
  await expect(text.locator('b')).toHaveCount(0);
});

Then('壊れた Bearer 付きでも公開記事は表題・公開開始・本文だけで返る', async ({ request }) => {
  const broken = { ...STORE_HEADERS, Authorization: 'Bearer broken-token-value' };
  const list = await request.get('/api/articles/public', { headers: broken, params: { size: 100 } });
  expect(list.status()).toBe(200);
  const row = (await list.json()).content.find((a: { id: string }) => a.id === articleId);
  expect(Object.keys(row).sort()).toEqual(['id', 'publish_from', 'title']);
  const detail = await request.get(`/api/articles/public/${articleId}`, { headers: broken });
  expect(detail.status()).toBe(200);
  const article = await detail.json();
  expect(Object.keys(article).sort()).toEqual(['body', 'id', 'publish_from', 'title']);
  expect(article.body).toBe(body());
});

When('記事を非公開に戻す', async ({ request }) => {
  const article = await setPublication(request, { published: false });
  expect(article.displayed).toBe(false);
});

Then('お知らせ一覧から記事が消え記事ページは見つからない', async ({ page, request }) => {
  await page.goto('/news');
  await expect(page.getByRole('heading', { name: 'お知らせ', exact: true })).toBeVisible();
  await expect(page.getByRole('link').filter({ hasText: title })).toHaveCount(0);
  expect((await request.get(`/api/articles/public/${articleId}`, { headers: STORE_HEADERS })).status()).toBe(404);
  await page.goto(`/news/${articleId}`);
  await expect(page.getByRole('heading', { name: 'ページが見つかりませんでした' })).toBeVisible();
});

async function setCover(request: APIRequestContext, cover: string | null) {
  const response = await request.put(`/api/articles/${articleId}`, {
    headers: headers(),
    data: { title, body: body(), cover_image_url: cover, version },
  });
  expect(response.status(), await response.text()).toBe(200);
  const article = await response.json();
  version = article.version;
  return article;
}

When('記事管理者が表紙画像をアップロードして記事に付ける', async ({ request }) => {
  // 記事管理の権限だけで公開アセットへ上げられる
  const uploaded = await uploadStoreImage(request, token, 'article-cover');
  expect(uploaded.status(), await uploaded.text()).toBe(201);
  coverUrl = (await uploaded.json()).url;
  expect(coverUrl).toMatch(/^\/static\/uploads\/public\/\d+\/article-cover\/[0-9a-f-]{36}\.png$/);
  expect((await setCover(request, coverUrl)).cover_image_url).toBe(coverUrl);
});

Then('お知らせ一覧と記事ページに表紙画像が表示される', async ({ page }) => {
  await page.goto('/news');
  const card = page.getByRole('link').filter({ hasText: title });
  await expect(card.locator(`img[src="${coverUrl}"]`)).toBeVisible();
  // 画像の実体が訪問者のブラウザから読めることまで確かめる
  await expect.poll(() => card.locator('img').evaluate(img => (img as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  await page.goto(`/news/${articleId}`);
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(page.locator(`img[src="${coverUrl}"]`)).toBeVisible();
});

When('記事の表紙画像を外す', async ({ request }) => {
  expect((await setCover(request, null)).cover_image_url).toBeUndefined();
});

Then('お知らせ一覧と記事ページから表紙画像が消える', async ({ page }) => {
  await page.goto('/news');
  const card = page.getByRole('link').filter({ hasText: title });
  await expect(card).toBeVisible();
  await expect(card.locator('img')).toHaveCount(0);
  await page.goto(`/news/${articleId}`);
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(page.locator(`img[src="${coverUrl}"]`)).toHaveCount(0);
});

// 途中で失敗しても掲出したまま残さない。ベストエフォート。
After(async ({ request }) => {
  if (articleId && token) {
    await request
      .delete(`/api/articles/${articleId}`, { headers: headers(), params: { version } })
      .catch(() => {});
  }
  token = '';
  articleId = '';
  version = 0;
  title = '';
  suffix = '';
  coverUrl = '';
});
