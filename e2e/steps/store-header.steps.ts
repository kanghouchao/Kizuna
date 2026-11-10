import { expect } from '@playwright/test';
import { createBdd, DataTable } from 'playwright-bdd';
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  createPermissionRole,
  createStaffFixture,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginUser,
} from './store-api';

const { Given, Then } = createBdd();

const ENDPOINTS: Record<string, string> = {
  キャスト一覧: '/api/casts',
  公開店舗設定: '/api/config/public',
  予約申請の連絡同意文面: '/api/order-applications/public/contact-consent',
};
let specificToken = '';
let twoStoreToken = '';
let store2Id = '';

Given('キャスト管理の権限を持つ単店と複数店舗の授権者がいる', async ({ request }) => {
  const admin = await loginUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  const suffix = Date.now().toString();
  const password = `StoreHeader-${suffix}-fixture`;
  const role = await createPermissionRole(request, admin, `店舗ヘッダー検証-${suffix}`, ['CAST_MANAGE']);
  const stores = await getAuthorizedStores(request, await loginAsStoreAdmin(request));
  store2Id = String(stores.find((store) => String(store.id) !== STORE1_ID)!.id);
  const specific = `store-header-specific-${suffix}@kizuna.test`;
  const twoStore = `store-header-two-store-${suffix}@kizuna.test`;
  await createStaffFixture(request, admin, specific, password, [role], [Number(STORE1_ID)]);
  await createStaffFixture(request, admin, twoStore, password, [role], [Number(STORE1_ID), Number(store2Id)]);
  specificToken = await loginUser(request, specific, password);
  twoStoreToken = await loginUser(request, twoStore, password);
});

// 表の「店舗1」「店舗2」は実行時の id に、「なし」はヘッダーの省略に読み替え、それ以外はそのまま送る。
Then(
  /^(単店の授権者|複数店舗の授権者|匿名の利用者)が(キャスト一覧|公開店舗設定|予約申請の連絡同意文面)を店舗ヘッダーごとに要求すると次の応答になる$/,
  async ({ request }, caller: string, endpoint: string, table: DataTable) => {
    const token: string | undefined = ({ 単店の授権者: specificToken, 複数店舗の授権者: twoStoreToken } as Record<string, string>)[caller];
    const storeIds: Record<string, string> = { 店舗1: STORE1_ID, 店舗2: store2Id };
    const expected = table.hashes();
    const actual: Record<string, string>[] = [];
    for (const row of expected) {
      const header = row['店舗ヘッダー'];
      const response = await request.get(ENDPOINTS[endpoint], {
        headers: {
          ...(header === 'なし' ? {} : { 'X-Store-ID': storeIds[header] ?? header }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      actual.push({ 店舗ヘッダー: header, 応答: String(response.status()) });
    }
    expect(actual).toEqual(expected);
  },
);
