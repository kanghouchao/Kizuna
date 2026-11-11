import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { StoreContextProvider, useStoreContext } from '../StoreContext';
import { authApi } from '../../api/auth';
import type { AuthorizedStore } from '../types';

jest.mock('../../api/auth', () => ({
  authApi: { stores: jest.fn() },
}));

let pathname = '/admin/dashboard';
jest.mock('next/navigation', () => ({ usePathname: () => pathname }));

const mockedStores = authApi.stores as jest.MockedFunction<typeof authApi.stores>;

const storeA: AuthorizedStore = { id: 1, name: '店舗A', status: 'ACTIVE' };
const storeB: AuthorizedStore = { id: 2, name: '店舗B', status: 'PREPARING' };

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <StoreContextProvider>{children}</StoreContextProvider>
);

describe('StoreContextProvider（店舗コンテキストの deep module）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    pathname = '/admin/dashboard';
    mockedStores.mockResolvedValue([storeA, storeB]);
  });

  describe('授権店舗の解決', () => {
    it('stores() の取得完了までは stores=null・currentStoreId 未確定', () => {
      mockedStores.mockReturnValue(new Promise(() => {}));

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      expect(result.current.stores).toBeNull();
      expect(result.current.currentStoreId).toBeUndefined();
    });

    it('授権店舗ゼロは空一覧として確定し、現在店舗は未確定のまま', async () => {
      mockedStores.mockResolvedValue([]);

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([]));
      expect(result.current.currentStoreId).toBeUndefined();
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
      pathname = '/store/1/orders';
      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));

      let resolveReload: (stores: AuthorizedStore[]) => void = () => {};
      mockedStores.mockReturnValueOnce(new Promise(resolve => (resolveReload = resolve)));
      act(() => result.current.reload());

      expect(result.current.stores).toEqual([storeA, storeB]);
      expect(result.current.currentStoreId).toBe('1');

      await act(async () => resolveReload([{ ...storeA, status: 'PREPARING' }]));
      expect(result.current.currentStore?.status).toBe('PREPARING');
    });

    it('取り直しに失敗したら直前の一覧と現在店舗を保ったまま loadFailed を立てる', async () => {
      jest.spyOn(console, 'error').mockImplementation(() => {});
      pathname = '/store/1/orders';
      const { result } = renderHook(() => useStoreContext(), { wrapper });
      await waitFor(() => expect(result.current.currentStoreId).toBe('1'));

      mockedStores.mockRejectedValueOnce(new Error('network'));
      act(() => result.current.reload());

      await waitFor(() => expect(result.current.loadFailed).toBe(true));
      expect(result.current.stores).toEqual([storeA, storeB]);
      expect(result.current.currentStoreId).toBe('1');
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
    it('店舗の文脈の画面では URL の店舗 id を現在店舗にする', async () => {
      pathname = '/store/2/orders/5/edit';

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      expect(result.current.currentStoreId).toBe('2');
      await waitFor(() => expect(result.current.currentStore).toEqual(storeB));
    });

    it.each(['/admin/dashboard', '/store', '/store/abc/orders'])(
      '%s では現在店舗を持たない（一覧の先頭や前回の選択で補わない）',
      async path => {
        pathname = path;

        const { result } = renderHook(() => useStoreContext(), { wrapper });

        await waitFor(() => expect(result.current.stores).toEqual([storeA, storeB]));
        expect(result.current.currentStoreId).toBeUndefined();
        expect(result.current.currentStore).toBeUndefined();
      }
    );

    it('URL の店舗が授権店舗に無ければ currentStore は無い（id は URL のまま）', async () => {
      pathname = '/store/99/orders';

      const { result } = renderHook(() => useStoreContext(), { wrapper });

      await waitFor(() => expect(result.current.stores).toEqual([storeA, storeB]));
      expect(result.current.currentStoreId).toBe('99');
      expect(result.current.currentStore).toBeUndefined();
    });
  });
});
