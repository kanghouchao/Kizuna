import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { StoreContextProvider, useStoreContext } from '../StoreContext';
import { platformAuthApi } from '../../api/platform';
import { getPlatformStoreId, setPlatformStore } from '@/shared/lib';
import type { PlatformStore } from '../types';

jest.mock('../../api/platform', () => ({
  platformAuthApi: { stores: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  getPlatformStoreId: jest.fn(),
  setPlatformStore: jest.fn(),
}));

const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedSetPlatformStore = setPlatformStore as jest.MockedFunction<typeof setPlatformStore>;

const storeA: PlatformStore = { id: 1, name: '店舗A', status: 'ACTIVE' };
const storeB: PlatformStore = { id: 2, name: '店舗B', status: 'PREPARING' };

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <StoreContextProvider>{children}</StoreContextProvider>
);

describe('StoreContextProvider（店舗コンテキストの deep module）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetStoreId.mockReturnValue(undefined);
    mockedStores.mockResolvedValue([storeA, storeB]);
  });

  describe('授権店舗の解決', () => {
    it('stores() の取得完了までは stores=null・currentStoreId 未確定', () => {
      mockedStores.mockReturnValue(new Promise(() => {}));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      expect(result.current.stores).toBeNull();
      expect(result.current.currentStoreId).toBeUndefined();
      expect(mockedSetPlatformStore).not.toHaveBeenCalled();
    });

    it('授権店舗ゼロは空一覧として確定し、現在店舗は未確定のまま', async () => {
      mockedStores.mockResolvedValue([]);

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.currentStoreId).toBeUndefined();
      expect(mockedSetPlatformStore).not.toHaveBeenCalled();
    });

    it('stores() が失敗したときは値を確定させず loadFailed を立てる', async () => {
      jest.spyOn(console, 'error').mockImplementation(() => {});
      mockedStores.mockRejectedValue(new Error('network'));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.loadFailed).toBe(true));
      expect(result.current.stores).toBeNull();
    });

    it('reload は取得をやり直し、成功すれば loadFailed が下りる', async () => {
      jest.spyOn(console, 'error').mockImplementation(() => {});
      mockedStores.mockRejectedValueOnce(new Error('network'));

      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.loadFailed).toBe(true));

      act(() => result.current.reload());

      await waitFor(() => expect(result.current.stores).toEqual([storeA, storeB]));
      expect(result.current.loadFailed).toBe(false);
    });

    it('取り直しの間も直前の一覧と現在店舗を保ち、届いた一覧で置き換える', async () => {
      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));

      let resolveReload: (stores: PlatformStore[]) => void = () => {};
      mockedStores.mockReturnValueOnce(new Promise(resolve => (resolveReload = resolve)));
      mockedGetStoreId.mockReturnValue('1');
      act(() => result.current.reload());

      expect(result.current.stores).toEqual([storeA, storeB]);
      expect(result.current.currentStoreId).toBe('1');

      await act(async () => resolveReload([{ ...storeA, status: 'PREPARING' }]));
      expect(result.current.currentStore?.status).toBe('PREPARING');
    });

    it('1つの provider 配下で複数の consumer が描画されても stores() は1回だけ呼ばれる', async () => {
      function Consumer({ label }: { label: string }) {
        const { stores } = useStoreContext();
        return <p>{`${label}:${stores?.length ?? 'loading'}`}</p>;
      }

      render(
        <StoreContextProvider>
          <Consumer label="a" />
          <Consumer label="b" />
        </StoreContextProvider>
      );

      await screen.findByText('a:2');
      expect(screen.getByText('b:2')).toBeInTheDocument();
      expect(mockedStores).toHaveBeenCalledTimes(1);
    });
  });

  describe('currentStoreId', () => {
    it('前回選択 cookie が授権店舗を指していればそれを現在店舗にする', async () => {
      mockedGetStoreId.mockReturnValue('2');

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.currentStoreId).toBe('2'));
      expect(result.current.currentStore).toEqual(storeB);
      expect(mockedSetPlatformStore).toHaveBeenCalledWith('2');
    });

    it.each([undefined, '99'])(
      'cookie が無い・授権外（%s）なら授権店舗の先頭にし、cookie へ書く',
      async cookie => {
        mockedGetStoreId.mockReturnValue(cookie);

        const { result } = renderHook(() => useStoreContext(), { wrapper });

        await waitFor(() => expect(result.current.currentStoreId).toBe('1'));
        expect(mockedSetPlatformStore).toHaveBeenCalledWith('1');
      }
    );

    it('cookie を書いてから現在店舗を出す（apiClient が同期に読むため）', async () => {
      const seen: (string | undefined)[] = [];
      mockedSetPlatformStore.mockImplementation(id => {
        mockedGetStoreId.mockReturnValue(String(id));
      });
      function Probe() {
        const { currentStoreId } = useStoreContext();
        if (currentStoreId !== undefined) seen.push(getPlatformStoreId());
        return null;
      }

      render(
        <StoreContextProvider>
          <Probe />
        </StoreContextProvider>
      );

      await waitFor(() => expect(seen.length).toBeGreaterThan(0));
      expect(seen[0]).toBe('1');
    });
  });

  describe('switchStore', () => {
    it('cookie を書き、現在店舗を差し替える', async () => {
      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));
      mockedSetPlatformStore.mockClear();

      act(() => result.current.switchStore(2));

      expect(mockedSetPlatformStore).toHaveBeenCalledWith(2);
      expect(result.current.currentStoreId).toBe('2');
      expect(result.current.currentStore).toEqual(storeB);
    });
  });
});
