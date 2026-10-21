import { fireEvent, render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { useAuth, useStoreContext } from '@/entities/user';
import { AdminShell } from '../AdminShell';

let mockPathname = '/store/orders';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/user', () => ({
  StoreContextProvider: ({ children }: { children: React.ReactNode }) => children,
  MeProvider: ({ children }: { children: React.ReactNode }) => children,
  useStoreContext: jest.fn(),
  useAuth: jest.fn(),
}));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const logout = jest.fn();
const reload = jest.fn();

type StoreContextValue = ReturnType<typeof useStoreContext>;
const storeContext = (overrides: Partial<StoreContextValue>): StoreContextValue => ({
  stores: [{ id: 1, name: '店舗A', status: 'ACTIVE' }],
  currentStoreId: '1',
  currentStore: { id: 1, name: '店舗A', status: 'ACTIVE' },
  loadFailed: false,
  reload,
  switchStore: jest.fn(),
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
    mockPathname = '/store/orders';
    mockedUseAuth.mockReturnValue({ logout } as unknown as ReturnType<typeof useAuth>);
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

    it('授権店舗ゼロは行き止まりを名乗り、ログアウトは利用者が押す', () => {
      mockedUseStoreContext.mockReturnValue(
        storeContext({ stores: [], currentStoreId: undefined, currentStore: undefined })
      );

      renderShell();

      expect(screen.getByRole('alert')).toHaveTextContent(
        'アクセス可能な店舗がありません。管理者にお問い合わせください'
      );
      expect(logout).not.toHaveBeenCalled();
      fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
      expect(logout).toHaveBeenCalled();
      expect(screen.queryByText('本文')).not.toBeInTheDocument();
    });

    it('現在店舗が替わるとページを再マウントする', () => {
      const { rerender } = renderShell();
      expect(mounts).toHaveBeenCalledTimes(1);

      mockedUseStoreContext.mockReturnValue(
        storeContext({ currentStoreId: '2', currentStore: { id: 2, name: '店舗B' } })
      );
      rerender(
        <AdminShell sidebar={<nav>サイドバー</nav>} header={<header>ヘッダー</header>}>
          <Page />
        </AdminShell>
      );

      expect(mounts).toHaveBeenCalledTimes(2);
    });
  });

  describe('/platform 配下', () => {
    beforeEach(() => {
      mockPathname = '/platform/dashboard';
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

    it('現在店舗が替わってもページを再マウントしない', () => {
      const { rerender } = renderShell();

      mockedUseStoreContext.mockReturnValue(storeContext({ currentStoreId: '2' }));
      rerender(
        <AdminShell sidebar={<nav>サイドバー</nav>} header={<header>ヘッダー</header>}>
          <Page />
        </AdminShell>
      );

      expect(mounts).toHaveBeenCalledTimes(1);
    });
  });
});
