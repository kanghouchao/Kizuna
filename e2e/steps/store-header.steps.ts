import { expect } from '@playwright/test';
import { createBdd, DataTable } from 'playwright-bdd';
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  createPermissionRole,
  createStaffFixture,
  createStoreStaffFixture,
  getAuthorizedStores,
  loginAsStoreAdmin,
  loginUser,
} from './store-api';

const { Given, Then } = createBdd();

const ENDPOINTS: Record<string, string> = {
  キャスト一覧: '/api/store/casts',
  公開店舗設定: '/api/store/config/public',
  予約申請の連絡同意文面: '/api/store/order-applications/public/contact-consent',
};
let specificToken = '';
let allStoresToken = '';
let store2Id = '';

Given('キャスト管理の権限を持つ個別店舗と全店舗の授権者がいる', async ({ request }) => {
  const admin = await loginUser(request, 'admin@kizuna.test', ADMIN_PASSWORD);
  const suffix = Date.now().toString();
  const password = `StoreHeader-${suffix}-fixture`;
  const role = await createPermissionRole(request, admin, `店舗ヘッダー検証-${suffix}`, ['CAST_MANAGE']);
  const specific = `store-header-specific-${suffix}@kizuna.test`;
  const allStores = `store-header-all-${suffix}@kizuna.test`;
  await createStoreStaffFixture(request, admin, specific, password, [role], [Number(STORE1_ID)]);
  await createStaffFixture(request, admin, allStores, password, [role]);
  specificToken = await loginUser(request, specific, password);
  allStoresToken = await loginUser(request, allStores, password);
  const stores = await getAuthorizedStores(request, await loginAsStoreAdmin(request));
  store2Id = String(stores.find((store) => String(store.id) !== STORE1_ID)!.id);
});

// 表の「店舗1」「店舗2」は実行時の id に、「なし」はヘッダーの省略に読み替え、それ以外はそのまま送る。
Then(
  /^(個別店舗の授権者|全店舗の授権者|匿名の利用者)が(キャスト一覧|公開店舗設定|予約申請の連絡同意文面)を店舗ヘッダーごとに要求すると次の応答になる$/,
  async ({ request }, caller: string, endpoint: string, table: DataTable) => {
    const token: string | undefined = ({ 個別店舗の授権者: specificToken, 全店舗の授権者: allStoresToken } as Record<string, string>)[caller];
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
