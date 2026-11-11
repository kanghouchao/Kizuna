import { fireEvent, render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { useStoreContext } from '@/entities/user';
import { notify } from '@/shared/notify';
import { AdminShell } from '../AdminShell';

let mockPathname = '/store/1/orders';
const replace = jest.fn();
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ replace }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('../../model/landing', () => ({ useLandingPath: () => '/admin/dashboard' }));

jest.mock('@/entities/menu', () => ({
  MenuProvider: ({ children }: { children: React.ReactNode }) => children,
}));

jest.mock('@/entities/user', () => ({
  StoreContextProvider: ({ children }: { children: React.ReactNode }) => children,
  MeProvider: ({ children }: { children: React.ReactNode }) => children,
  useStoreContext: jest.fn(),
}));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const reload = jest.fn();

type StoreContextValue = ReturnType<typeof useStoreContext>;
const storeContext = (overrides: Partial<StoreContextValue>): StoreContextValue => ({
  stores: [{ id: 1, name: '店舗A', status: 'ACTIVE' }],
  currentStoreId: '1',
  currentStore: { id: 1, name: '店舗A', status: 'ACTIVE' },
  loadFailed: false,
  reload,
  ...overrides,
});

const mounts = jest.fn();
function Page() {
  useEffect(() => {
    mounts();
  }, []);
  return <p>本文</p>;
}

const renderShell = () =>
  render(
    <AdminShell sidebar={<nav>サイドバー</nav>} header={<header>ヘッダー</header>}>
      <Page />
    </AdminShell>
  );

describe('AdminShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/store/1/orders';
    mockedUseStoreContext.mockReturnValue(storeContext({}));
  });

  it('サイドバー・ヘッダー・本文を一つの殻に描く', () => {
    renderShell();

    expect(screen.getByText('サイドバー')).toBeInTheDocument();
    expect(screen.getByText('ヘッダー')).toBeInTheDocument();
    expect(screen.getByText('本文')).toBeInTheDocument();
  });

  describe('/store 配下', () => {
    it('授権店舗の読み込み中は本文を描かず読み込み中を出す', () => {
      mockedUseStoreContext.mockReturnValue(
        storeContext({ stores: null, currentStoreId: undefined, currentStore: undefined })
      );

      renderShell();

      expect(screen.getByText('読み込み中...')).toBeInTheDocument();
      expect(screen.queryByText('本文')).not.toBeInTheDocument();
    });

    it('取得失敗は再試行つきの失敗表示にする', () => {
      mockedUseStoreContext.mockReturnValue(
        storeContext({
          stores: null,
          currentStoreId: undefined,
          currentStore: undefined,
          loadFailed: true,
        })
      );

      renderShell();
      fireEvent.click(screen.getByRole('button', { name: '再試行' }));

      expect(screen.getByText('店舗情報の取得に失敗しました')).toBeInTheDocument();
      expect(reload).toHaveBeenCalled();
      expect(screen.queryByText('本文')).not.toBeInTheDocument();
    });

    it.each([
      ['授権店舗に無い店舗の', '/store/99/orders', [{ id: 1, name: '店舗A' }], '99'],
      ['店舗 id の無い', '/store/orders', [{ id: 1, name: '店舗A' }], undefined],
      ['授権店舗が空の者が開いた店舗の', '/store/1/orders', [], '1'],
    ])('%s画面は本文を描かず、警告して着地へ戻す', (_, pathname, stores, id) => {
      mockPathname = pathname;
      mockedUseStoreContext.mockReturnValue(
        storeContext({ stores, currentStoreId: id, currentStore: undefined })
      );

      renderShell();

      expect(replace).toHaveBeenCalledWith('/admin/dashboard');
      expect(notify.warning).toHaveBeenCalledWith('この店舗の画面は開けません');
      expect(screen.queryByText('本文')).not.toBeInTheDocument();
      expect(mounts).not.toHaveBeenCalled();
    });

    it('現在店舗が替わるとページを再マウントする', () => {
      const { rerender } = renderShell();
      expect(mounts).toHaveBeenCalledTimes(1);

      mockPathname = '/store/2/orders';
      mockedUseStoreContext.mockReturnValue(
        storeContext({
          stores: [
            { id: 1, name: '店舗A' },
            { id: 2, name: '店舗B' },
          ],
          currentStoreId: '2',
          currentStore: { id: 2, name: '店舗B' },
        })
      );
      rerender(
        <AdminShell sidebar={<nav>サイドバー</nav>} header={<header>ヘッダー</header>}>
          <Page />
        </AdminShell>
      );

      expect(mounts).toHaveBeenCalledTimes(2);
    });
  });

  describe('/admin 配下', () => {
    beforeEach(() => {
      mockPathname = '/admin/dashboard';
    });

    it('店舗の読み込み中・失敗・ゼロでも本文を描く', () => {
      mockedUseStoreContext.mockReturnValue(
        storeContext({
          stores: null,
          currentStoreId: undefined,
          currentStore: undefined,
          loadFailed: true,
        })
      );

      renderShell();

      expect(screen.getByText('本文')).toBeInTheDocument();
    });
  });
});
