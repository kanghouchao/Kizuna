import { render, renderHook, screen, waitFor } from '@testing-library/react';
import { StoreContextProvider, useStoreContext } from '../StoreContext';
import { platformAuthApi } from '../../api/platform';
import { isPlatformSession, getPlatformStoreId, setPlatformStore } from '@/shared/lib';
import type { PlatformMeResponse, PlatformStoreScopeType } from '../types';

let mockPathname = '/platform/dashboard';
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('../../api/platform', () => ({
  platformAuthApi: { me: jest.fn(), stores: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isPlatformSession: jest.fn(),
  getPlatformStoreId: jest.fn(),
  setPlatformStore: jest.fn(),
}));

const mockedMe = platformAuthApi.me as jest.MockedFunction<typeof platformAuthApi.me>;
const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;
const mockedIsPlatformSession = isPlatformSession as jest.MockedFunction<typeof isPlatformSession>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedSetPlatformStore = setPlatformStore as jest.MockedFunction<typeof setPlatformStore>;

const meResponse = (
  store_bridge: boolean,
  store_scope_type: PlatformStoreScopeType = 'ALL_STORES',
  store_ids: number[] = []
): PlatformMeResponse => ({
  email: 'staff@example.com',
  display_name: 'スタッフ',
  user_type: 'STAFF',
  capabilities: [],
  console: 'store',
  store_bridge,
  store_scope_type,
  store_ids,
});

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <StoreContextProvider>{children}</StoreContextProvider>
);

describe('StoreContextProvider（店舗コンテキストの deep module #428）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/platform/dashboard';
    mockedIsPlatformSession.mockReturnValue(false);
    mockedGetStoreId.mockReturnValue(undefined);
    mockedMe.mockResolvedValue(meResponse(true));
    mockedStores.mockResolvedValue([]);
  });

  describe('授権店舗の解決', () => {
    it('取得前の初期値は null（読み込み中）', () => {
      mockedMe.mockReturnValue(new Promise(() => {}));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      expect(result.current.stores).toBeNull();
      expect(result.current.storeBridge).toBeNull();
    });

    it('store_bridge=true なら stores() の結果をそのまま返す', async () => {
      mockedMe.mockResolvedValue(meResponse(true));
      mockedStores.mockResolvedValue([
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ]);

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() =>
        expect(result.current.stores).toEqual([
          { id: 1, name: '店舗A' },
          { id: 2, name: '店舗B' },
        ])
      );
      expect(result.current.storeBridge).toBe(true);
    });

    it('store_bridge=false なら stores() を呼ばず空一覧を返す', async () => {
      mockedMe.mockResolvedValue(meResponse(false));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.storeBridge).toBe(false);
      expect(mockedStores).not.toHaveBeenCalled();
    });

    it('me() が失敗したときは空一覧・storeBridge=false を返す', async () => {
      mockedMe.mockRejectedValue(new Error('500'));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.storeBridge).toBe(false);
      expect(mockedStores).not.toHaveBeenCalled();
    });

    it('1つの provider 配下で複数の consumer が描画されても me() は1回だけ呼ばれる', async () => {
      mockedMe.mockResolvedValue(meResponse(true));
      mockedStores.mockResolvedValue([{ id: 1, name: '店舗A' }]);

      function Consumer() {
        const { stores } = useStoreContext();
        return <span>{stores?.length ?? 'loading'}</span>;
      }

      render(
        <StoreContextProvider>
          <Consumer />
          <Consumer />
        </StoreContextProvider>
      );

      await waitFor(() => expect(screen.getAllByText('1')).toHaveLength(2));
      expect(mockedMe).toHaveBeenCalledTimes(1);
      expect(mockedStores).toHaveBeenCalledTimes(1);
    });
  });

  describe('currentStoreId', () => {
    it('pathname 由来の storeId を最優先する', async () => {
      mockPathname = '/store/7/orders';
      mockedIsPlatformSession.mockReturnValue(true);
      mockedGetStoreId.mockReturnValue('3');

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).not.toBeNull());
      expect(result.current.currentStoreId).toBe('7');
    });

    it('pathname に storeId が無ければ平台セッション時のみ前回選択 cookie に fallback する', async () => {
      mockPathname = '/platform/dashboard';
      mockedIsPlatformSession.mockReturnValue(true);
      mockedGetStoreId.mockReturnValue('3');

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.currentStoreId).toBe('3'));
    });

    it('平台セッションでなければ cookie fallback しない', async () => {
      mockPathname = '/platform/dashboard';
      mockedIsPlatformSession.mockReturnValue(false);
      mockedGetStoreId.mockReturnValue('3');

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).not.toBeNull());
      expect(result.current.currentStoreId).toBeUndefined();
    });
  });

  describe('switchStore', () => {
    it('店舗スコープページからの切替は現在地の storeId を差し替えて push する', async () => {
      mockPathname = '/store/1/orders';

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.stores).not.toBeNull());

      result.current.switchStore(2);

      expect(mockedSetPlatformStore).toHaveBeenCalledWith(2);
      expect(mockPush).toHaveBeenCalledWith('/store/2/orders');
    });

    it('店舗スコープ外からの切替は /store/{id}/dashboard へ push する', async () => {
      mockPathname = '/platform/dashboard';

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.stores).not.toBeNull());

      result.current.switchStore(2);

      expect(mockPush).toHaveBeenCalledWith('/store/2/dashboard');
    });

    it('前回選択 cookie と同じ店舗でも pathStoreId 未確定なら push が発火する（#413 Fix5-1）', async () => {
      // no-op 判定は pathStoreId のみで行う。currentStoreId（cookie fallback 込み）で比較すると
      // /platform 側で前回選択と同じ店舗をクリックした単一店舗ユーザーが遷移できなくなる。
      mockPathname = '/platform/dashboard';
      mockedIsPlatformSession.mockReturnValue(true);
      mockedGetStoreId.mockReturnValue('1');

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));

      result.current.switchStore(1);

      expect(mockPush).toHaveBeenCalledWith('/store/1/dashboard');
    });

    it('現在地の pathStoreId と同一店舗への切替は no-op（push しない）', async () => {
      mockPathname = '/store/1/orders';

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.stores).not.toBeNull());

      result.current.switchStore(1);

      expect(mockPush).not.toHaveBeenCalled();
    });
  });
});
