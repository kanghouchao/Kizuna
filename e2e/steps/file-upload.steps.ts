import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { loginAsStoreAdmin, STORE_HEADERS } from './store-api';

const { When, Then } = createBdd();
const image = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQAAAABJRU5ErkJggg==',
  'base64'
);
let publicUrl: string;

When('店長が画像ファイルをアップロードする', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const response = await request.post('/api/files', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    multipart: { file: { name: 'storage-probe.png', mimeType: 'image/png', buffer: image } },
  });
  expect(response.status()).toBe(201);
  const body = await response.json();
  expect(body.size).toBe(image.length);
  expect(body.url).toMatch(/^\/static\/uploads\/public\//);
  publicUrl = body.url;
});

Then('返された公開 URL から認証なしで同じ画像を取得できる', async ({ request }) => {
  const response = await request.get(publicUrl);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toBe('image/png');
  expect(await response.body()).toEqual(image);
  expect((await request.head(publicUrl)).status()).toBe(200);
});

Then('公開ストレージへの匿名書き込みと一覧取得は拒否される', async ({ request }) => {
  expect((await request.put(publicUrl, { data: image })).status()).toBe(403);
  expect((await request.get('/static/uploads?list-type=2')).status()).toBe(403);
});
