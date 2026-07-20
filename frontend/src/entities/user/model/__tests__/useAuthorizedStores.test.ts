import { renderHook, waitFor } from '@testing-library/react';
import { useAuthorizedStores } from '../useAuthorizedStores';
import { platformAuthApi } from '@/entities/user/api/platform';
import type {
  PlatformCapability,
  PlatformMeResponse,
  PlatformStoreScopeType,
} from '@/entities/user';

jest.mock('@/entities/user/api/platform', () => ({
  platformAuthApi: { stores: jest.fn(), me: jest.fn() },
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

describe('useAuthorizedStores（授権店舗解決の共有 hook #413 Fix7）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 既定は実運用の store-console 能力を持つユーザー（到達資格を満たす）。
    mockedMe.mockResolvedValue(meResponse(['ORDER_MANAGE']));
  });

  it('取得前の初期値は null（読み込み中）', () => {
    mockedMe.mockReturnValue(new Promise(() => {}));
    mockedStores.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useAuthorizedStores());

    expect(result.current).toBeNull();
  });

  it('通常の実運用店舗能力保持者は stores() 成功時にその一覧をそのまま返す', async () => {
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() =>
      expect(result.current).toEqual([
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ])
    );
  });

  it('実運用の store-console 能力が無い（SHARED 能力のみ）ユーザーは stores() が非空でも空一覧を返す（#413 Fix5-3）', async () => {
    // /platform/stores/me は SHARED 能力 STORE_VIEW でも非空を返すため能力ゲートが要る。
    mockedMe.mockResolvedValue(meResponse(['STORE_VIEW']));
    mockedStores.mockResolvedValue([{ id: 1, name: '店舗A' }]);

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it('STORE_VIEW を持たない実運用店舗能力保持者は、stores() が 403 でも SPECIFIC_STORES の store_ids から単一店舗を返す（#413 Fix6-1）', async () => {
    // STORE_PROFILE_MANAGE のみの保持者は stores() が 403。/platform/me の store_ids をフォールバック源にする。
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'SPECIFIC_STORES', [7]));
    mockedStores.mockRejectedValue(new Error('403'));

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() => expect(result.current).toEqual([{ id: 7, name: '店舗 #7' }]));
  });

  it('STORE_VIEW を持たない実運用店舗能力保持者は、stores() が 403 でも SPECIFIC_STORES の store_ids を「店舗 #id」で複数返す（#413 Fix6-1）', async () => {
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'SPECIFIC_STORES', [7, 9]));
    mockedStores.mockRejectedValue(new Error('403'));

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() =>
      expect(result.current).toEqual([
        { id: 7, name: '店舗 #7' },
        { id: 9, name: '店舗 #9' },
      ])
    );
  });

  it('ALL_STORES かつ STORE_VIEW 欠如の実運用店舗能力保持者は store_ids が空のため空一覧を返す（既知の残存ギャップ・退行なし #413 Fix6-1）', async () => {
    // ALL_STORES 時は /platform/me の store_ids が空（バリデーション制約）でフォールバック源が無い。
    mockedMe.mockResolvedValue(meResponse(['STORE_PROFILE_MANAGE'], 'ALL_STORES', []));
    mockedStores.mockRejectedValue(new Error('403'));

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() => expect(result.current).toEqual([]));
  });

  it('me() が失敗したときは空一覧を返す（セッション異常。401 は apiClient が再ログインへ誘導）', async () => {
    mockedMe.mockRejectedValue(new Error('500'));
    mockedStores.mockResolvedValue([{ id: 1, name: '店舗A' }]);

    const { result } = renderHook(() => useAuthorizedStores());

    await waitFor(() => expect(result.current).toEqual([]));
  });
});
