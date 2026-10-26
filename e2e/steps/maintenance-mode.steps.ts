import { expect, type APIRequestContext } from '@playwright/test';
import { createBdd } from 'playwright-bdd';
import { ADMIN_PASSWORD, STORE_HEADERS, getMe, loginAsStoreAdmin, loginUser, uploadStoreImage } from './store-api';

const { Given, When, Then, After } = createBdd();

const GROUP_ADMIN_EMAIL = 'admin@kizuna.test';
const PUBLIC_CONFIG = '/api/config/public';

// 設定の更新はキャッシュを消してから応答するため、次の要求から新しい値で判定される。
async function setMaintenanceMode(request: APIRequestContext, enabled: boolean): Promise<void> {
  const token = await loginUser(request, GROUP_ADMIN_EMAIL, ADMIN_PASSWORD);
  const response = await request.put('/api/system-configs/maintenance_mode', {
    headers: { Authorization: `Bearer ${token}` },
    data: { config_value: String(enabled) },
  });
  expect(response.status(), await response.text()).toBe(200);
}

Given('メンテナンスモードが無効で公開店舗設定を取得できる', async ({ request }) => {
  await setMaintenanceMode(request, false);
  const response = await request.get(PUBLIC_CONFIG, { headers: STORE_HEADERS });
  expect(response.status(), await response.text()).toBe(200);
});

When(/^管理者がメンテナンスモードを(有効|無効)にする$/, async ({ request }, mode: string) => {
  await setMaintenanceMode(request, mode === '有効');
});

Then('公開店舗設定は店舗ヘッダーの有無にかかわらず 503 を返す', async ({ request }) => {
  const withHeader = await request.get(PUBLIC_CONFIG, { headers: STORE_HEADERS });
  const withoutHeader = await request.get(PUBLIC_CONFIG);
  expect([withHeader.status(), withoutHeader.status()]).toEqual([503, 503]);
});

Then(
  'ログイン・本人情報・システム設定・予約申請の連絡同意文面・ファイルのアップロードは止まらない',
  async ({ request }) => {
    const admin = await loginUser(request, GROUP_ADMIN_EMAIL, ADMIN_PASSWORD);
    expect((await getMe(request, admin)).email).toBe(GROUP_ADMIN_EMAIL);
    const configs = await request.get('/api/system-configs', { headers: { Authorization: `Bearer ${admin}` } });
    expect(configs.status(), await configs.text()).toBe(200);
    const maintenance = ((await configs.json()) as { config_key: string; config_value: string }[])
      .find((config) => config.config_key === 'maintenance_mode');
    expect(maintenance?.config_value).toBe('true');
    const consent = await request.get('/api/order-applications/public/contact-consent', { headers: STORE_HEADERS });
    expect(consent.status(), await consent.text()).toBe(200);
    const upload = await uploadStoreImage(request, await loginAsStoreAdmin(request));
    expect(upload.status(), await upload.text()).toBe(201);
  },
);

Then('公開店舗設定を再び取得できる', async ({ request }) => {
  const response = await request.get(PUBLIC_CONFIG, { headers: STORE_HEADERS });
  expect(response.status(), await response.text()).toBe(200);
});

// 失敗や中断でも無効へ戻す。既知の固定値の復元なので番兵で守らず、失敗はそのまま表に出す。
After({ tags: '@maintenance-mode' }, async ({ request }) => {
  await setMaintenanceMode(request, false);
});
