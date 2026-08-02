import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { StoreContextProvider, useStoreContext } from '../StoreContext';
import { platformAuthApi } from '../../api/platform';
import {
  isPlatformSession,
  getPlatformStoreId,
  readTokenClaims,
  setPlatformStore,
} from '@/shared/lib';
import type { TokenClaims } from '@/shared/lib';

let mockPathname = '/platform/dashboard';
const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('../../api/platform', () => ({
  platformAuthApi: { stores: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isPlatformSession: jest.fn(),
  getPlatformStoreId: jest.fn(),
  readTokenClaims: jest.fn(),
  setPlatformStore: jest.fn(),
}));

const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;
const mockedIsPlatformSession = isPlatformSession as jest.MockedFunction<typeof isPlatformSession>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;
const mockedSetPlatformStore = setPlatformStore as jest.MockedFunction<typeof setPlatformStore>;

const staffClaims = (storeBridge: boolean): TokenClaims => ({
  authorities: ['PERM_ORDER_MANAGE'],
  userType: 'STAFF',
  storeBridge,
});

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <StoreContextProvider>{children}</StoreContextProvider>
);

describe('StoreContextProvider（店舗コンテキストの deep module）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/platform/dashboard';
    mockedIsPlatformSession.mockReturnValue(false);
    mockedGetStoreId.mockReturnValue(undefined);
    mockedReadClaims.mockReturnValue(staffClaims(true));
    mockedStores.mockResolvedValue([]);
  });

  describe('授権店舗の解決', () => {
    it('stores() の取得完了までは stores=null（読み込み中）で、資格は同期に確定する', () => {
      mockedStores.mockReturnValue(new Promise(() => {}));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      expect(result.current.stores).toBeNull();
      expect(result.current.storeBridge).toBe(true);
    });

    it('storeBridge=true（token claim）なら stores() の結果をそのまま返す', async () => {
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

    it('storeBridge=false なら stores() を呼ばず空一覧を返す', async () => {
      mockedReadClaims.mockReturnValue(staffClaims(false));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.storeBridge).toBe(false);
      expect(mockedStores).not.toHaveBeenCalled();
    });

    it('token が無い・壊れている（claims=null）ときは資格なしとして扱う', async () => {
      // 未認証は資格なし側に倒す。最初の API 呼び出しの 401 がログインへの誘導を担う。
      mockedReadClaims.mockReturnValue(null);

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.storeBridge).toBe(false);
      expect(mockedStores).not.toHaveBeenCalled();
    });

    it('stores() が失敗したときは値を確定させず loadFailed を立てる', async () => {
      // 空一覧へ畳むと、通信障害が「授権店舗ゼロ」（入口がセッションを破棄する）に化ける。
      mockedStores.mockRejectedValue(new Error('500'));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.loadFailed).toBe(true));
      expect(result.current.stores).toBeNull();
    });

    it('reload は取得をやり直し、成功すれば loadFailed が下りる', async () => {
      mockedStores.mockRejectedValueOnce(new Error('500'));
      mockedStores.mockResolvedValue([{ id: 1, name: '店舗A' }]);

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.loadFailed).toBe(true));

      act(() => result.current.reload());

      await waitFor(() => expect(result.current.stores).toEqual([{ id: 1, name: '店舗A' }]));
      expect(result.current.loadFailed).toBe(false);
    });

    it('1つの provider 配下で複数の consumer が描画されても stores() は1回だけ呼ばれる', async () => {
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

    it('店舗スコープ外からの切替は入口ルートへ push する', async () => {
      // 着地先はメニュー由来のため、ここでは決められない。入口が解決する。
      // 選択した店舗は push の前に書かれる cookie が伝えるので、入口はそれを拾う。
      mockPathname = '/platform/dashboard';

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.stores).not.toBeNull());

      result.current.switchStore(2);

      expect(mockedSetPlatformStore).toHaveBeenCalledWith(2);
      expect(mockPush).toHaveBeenCalledWith('/store/entry');
    });

    it('前回選択 cookie と同じ店舗でも pathStoreId 未確定なら push が発火する', async () => {
      // no-op 判定は pathStoreId のみで行う。currentStoreId（cookie fallback 込み）で比較すると
      // /platform 側で前回選択と同じ店舗をクリックした単一店舗ユーザーが遷移できなくなる。
      mockPathname = '/platform/dashboard';
      mockedIsPlatformSession.mockReturnValue(true);
      mockedGetStoreId.mockReturnValue('1');

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));

      result.current.switchStore(1);

      expect(mockPush).toHaveBeenCalledWith('/store/entry');
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
