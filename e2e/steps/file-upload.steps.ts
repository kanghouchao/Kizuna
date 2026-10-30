import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import {
  loginAsStoreAdmin,
  loginUser,
  registerMember,
  SAMPLE_PNG as image,
  STORE_HEADERS,
  uploadStoreImage,
} from './store-api';

const { When, Then } = createBdd();
let publicUrl: string;

When('店長が画像ファイルをアップロードする', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const response = await uploadStoreImage(request, token);
  expect(response.status()).toBe(201);
  const body = await response.json();
  expect(body.size).toBe(image.length);
  expect(body.url).toMatch(/^\/static\/uploads\/public\/\d+\/store-logo\/[0-9a-f-]{36}\.png$/);
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

let memberUpload: Awaited<ReturnType<typeof uploadStoreImage>>;

When('会員が店舗の領域へ画像をアップロードする', async ({ request }) => {
  const email = `file-upload-e2e-${Date.now()}@kizuna.test`;
  await registerMember(request, email, 'pass12345', 'E2E画像会員');
  memberUpload = await uploadStoreImage(request, await loginUser(request, email, 'pass12345'));
});

Then('保存は権限がないため拒否される', async () => {
  expect(memberUpload.status()).toBe(403);
});

Then('店長が白名簿外の bucket を指定すると不正な要求として拒否される', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const response = await request.post('/api/files', {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
    multipart: {
      purpose: 'store-logo',
      bucket: 'private',
      file: { name: 'storage-probe.png', mimeType: 'image/png', buffer: image },
    },
  });
  expect(response.status()).toBe(400);
});

Then('店長が用途を指定しないか許可外の用途を指定すると不正な要求として拒否される', async ({ request }) => {
  const token = await loginAsStoreAdmin(request);
  const file = { name: 'storage-probe.png', mimeType: 'image/png', buffer: image };
  const missingAndOutside: Record<string, string | typeof file>[] = [{ file }, { purpose: 'avatar', file }];
  for (const multipart of missingAndOutside) {
    const response = await request.post('/api/files', {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${token}` },
      multipart,
    });
    expect(response.status()).toBe(400);
  }
});
