import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Header } from '../Header';
import { platformAuthApi } from '@/entities/user';
import { isPlatformSession, getPlatformStoreId, isStoreDomain } from '@/shared/lib';

// Headless UI の Menu は開閉時に ResizeObserver を使うが jsdom には無いため最小スタブを差す。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
global.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

const mockPush = jest.fn();
let mockPathname = '/platform/dashboard';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
  platformAuthApi: { stores: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isPlatformSession: jest.fn(),
  getPlatformStoreId: jest.fn(),
  setPlatformStore: jest.fn(),
  isStoreDomain: jest.fn(() => false),
}));

const mockedStores = platformAuthApi.stores as jest.MockedFunction<typeof platformAuthApi.stores>;
const mockedIsPlatformSession = isPlatformSession as jest.MockedFunction<typeof isPlatformSession>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedIsStoreDomain = isStoreDomain as jest.MockedFunction<typeof isStoreDomain>;

async function openSwitchAndSelect(name: string) {
  fireEvent.click(await screen.findByRole('button', { name: '店舗A' }));
  fireEvent.click(await screen.findByRole('menuitem', { name }));
}

describe('Header 店舗切替の常設化（#413）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/platform/dashboard';
    mockedIsPlatformSession.mockReturnValue(true);
    mockedGetStoreId.mockReturnValue('1');
  });

  it('平台セッションでも授権店舗が0件なら店舗切替は表示されない', async () => {
    mockedStores.mockResolvedValue([]);

    render(<Header />);

    await waitFor(() => expect(mockedStores).toHaveBeenCalled());
    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
  });

  it('授権店舗が1件なら console 値に依らず店舗切替が現在店舗名で表示される（混成束ユーザー含む）', async () => {
    mockedStores.mockResolvedValue([{ id: 1, name: '店舗A' }]);

    render(<Header />);

    expect(await screen.findByRole('button', { name: '店舗A' })).toBeInTheDocument();
  });

  it('店舗スコープページから切替すると現在地の storeId を差し替えて router.push する', async () => {
    mockPathname = '/store/1/orders';
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<Header />);
    await openSwitchAndSelect('店舗B');

    expect(mockPush).toHaveBeenCalledWith('/store/2/orders');
  });

  it('店舗スコープ外のページから切替すると /store/{id}/dashboard へ router.push する', async () => {
    mockPathname = '/platform/dashboard';
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<Header />);
    await openSwitchAndSelect('店舗B');

    expect(mockPush).toHaveBeenCalledWith('/store/2/dashboard');
  });

  it('店舗別ドメイン経由ではアカウント設定リンクが pathname 由来の storeId を含む店舗ルートを指す（#413 Fix2）', async () => {
    mockedIsStoreDomain.mockReturnValue(true);
    mockPathname = '/store/2/dashboard';
    mockedStores.mockResolvedValue([]);

    render(<Header />);

    expect(screen.getByRole('link', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/store/2/settings/account'
    );
  });

  it('pathname が別店舗へ変化するとラベルが新しい pathname 由来の店舗名へ追随する（#413 Fix1）', async () => {
    mockedGetStoreId.mockReturnValue('2');
    mockPathname = '/store/2/dashboard';
    mockedStores.mockResolvedValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    const { rerender } = render(<Header />);

    // 初期表示は pathname 由来の store 2（店舗B）。
    expect(await screen.findByRole('button', { name: '店舗B' })).toBeInTheDocument();

    // 店舗切替で URL が /store/1/... へ遷移した状態を再現する。
    mockPathname = '/store/1/dashboard';
    rerender(<Header />);

    // ラベルは古い店舗名のまま残らず、新しい pathname 由来の store 1（店舗A）へ更新される。
    expect(await screen.findByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗B' })).not.toBeInTheDocument();
  });
});
