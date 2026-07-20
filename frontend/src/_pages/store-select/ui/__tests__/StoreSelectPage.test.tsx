import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import StoreSelectPage from '../StoreSelectPage';
import { platformAuthApi } from '@/entities/user';
import type {
  PlatformCapability,
  PlatformMeResponse,
  PlatformStoreScopeType,
} from '@/entities/user';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock('@/entities/user', () => ({
  platformAuthApi: { stores: jest.fn(), me: jest.fn() },
  // 純関数のヘルパーは実体を使い、能力→到達資格の実ロジックをそのまま検証する（#413 Fix5-3）。
  hasStoreConsoleCapability: jest.requireActual('@/entities/user/model/storeConsoleCapability')
    .hasStoreConsoleCapability,
}));

const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;
const mockedMe = platformAuthApi.me as jest.MockedFunction<typeof platformAuthApi.me>;

const meResponse = (
  capabilities: PlatformCapability[],
  store_scope_type: PlatformStoreScopeType = 'ALL_STORES',
  store_ids: number[] = []
): PlatformMeResponse => ({
  email: 'staff@example.com',
  display_name: 'スタッフ',
  user_type: 'STAFF',
  capabilities,
  console: 'platform',
  store_scope_type,
  store_ids,
});

describe('StoreSelectPage（店舗未選択時の懒惰トリガー選択画面 #413）', () => {
  const originalHref = window.location.href;

  beforeEach(() => {
    jest.clearAllMocks();
    // 既定は実運用の store-console 能力を持つユーザー（到達資格を満たす）。
    mockedMe.mockResolvedValue(meResponse(['ORDER_MANAGE']));
  });

  afterEach(() => {
    window.history.pushState({}, '', originalHref);
  });

  it('授権店舗が0件のとき、遷移せず「アクセス可能な店舗がありません」を表示する', async () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedStores.mockResolvedValue([]);

    render(<StoreSelectPage />);

    expect(await screen.findByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('授権店舗が1件のとき、選択UIを出さず既定の next に storeId を埋めて自動遷移する', async () => {
    window.history.pushState({}, '', '/store/select');
    mockedStores.mockResolvedValue([{ id: 5, name: '店舗A' }]);

    render(<StoreSelectPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/5/dashboard'));
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗が複数件のとき、一覧を表示しクリックで next に storeId を埋めて遷移する', async () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<StoreSelectPage />);

    const storeB = await screen.findByRole('button', { name: '店舗B' });
    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();

    fireEvent.click(storeB);

    expect(mockReplace).toHaveBeenCalledWith('/store/2/orders');
  });

  it('STORE_VIEW を持たない実運用店舗能力保持者は、stores() が 403 でも SPECIFIC_STORES の store_ids で単一店舗を自動選択する（#413 Fix6-1）', async () => {
    // GET /platform/stores/me は SHARED 能力 STORE_VIEW でのみ守られるため、STORE_PROFILE_MANAGE のみの
    // 実運用店舗能力保持者は 403 になる。/platform/me は isAuthenticated() のみで常に到達可能なので、
    // SPECIFIC_STORES の store_ids を店舗 id のフォールバック源にする。
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'SPECIFIC_STORES', [7]));
    mockedStores.mockRejectedValue(new Error('403'));

    render(<StoreSelectPage />);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/store/7/orders'));
  });

  it('STORE_VIEW を持たない実運用店舗能力保持者は、stores() が 403 でも SPECIFIC_STORES の store_ids を「店舗 #id」で一覧表示する（#413 Fix6-1）', async () => {
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'SPECIFIC_STORES', [7, 9]));
    mockedStores.mockRejectedValue(new Error('403'));

    render(<StoreSelectPage />);

    const store9 = await screen.findByRole('button', { name: '店舗 #9' });
    expect(screen.getByRole('button', { name: '店舗 #7' })).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();

    fireEvent.click(store9);

    expect(mockReplace).toHaveBeenCalledWith('/store/9/orders');
  });

  it('ALL_STORES かつ STORE_VIEW 欠如の実運用店舗能力保持者は、store_ids が空のためフォールバックできず「アクセス可能な店舗がありません」を表示する（既知の残存ギャップ・退行なし #413 Fix6-1）', async () => {
    // ALL_STORES 時は /platform/me の store_ids が空（バリデーション制約）のためフォールバック源が無い。
    // 既存の 0 件時挙動と同じ表示に留める（B-lite の無改修方針の範囲外。真の解決は別途裁定）。
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'ALL_STORES', []));
    mockedStores.mockRejectedValue(new Error('403'));

    render(<StoreSelectPage />);

    expect(await screen.findByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('実運用の store-console 能力が無い（SHARED 能力のみ）ユーザーは、stores() が非空でも遷移せず「アクセス可能な店舗がありません」を表示する（#413 Fix5-3）', async () => {
    // 直リンク/陳旧リンクで /store/select に到達した能力無しユーザーは stores() が非空でも
    // 到達資格が無く、自動遷移/選択の末に StoreIdInterceptor で 403 になる。能力無しは空一覧扱い。
    window.history.pushState({}, '', '/store/select?next=%2Fstore%2Forders');
    mockedMe.mockResolvedValue(meResponse(['STORE_VIEW']));
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<StoreSelectPage />);

    expect(await screen.findByText('アクセス可能な店舗がありません')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });
});
