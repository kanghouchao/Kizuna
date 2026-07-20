import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from '../Header';
import { useAuthorizedStores } from '@/entities/user';
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

// 授権店舗の解決（能力ゲート・403 フォールバック含む）は useAuthorizedStores の責務で、
// その全ケースは useAuthorizedStores.test.ts で検証する。ここでは hook 出力に対する
// Header 自身の表示条件・切替遷移・ラベル追随を検証する（#413 Fix7）。
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
  useAuthorizedStores: jest.fn(),
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isPlatformSession: jest.fn(),
  getPlatformStoreId: jest.fn(),
  setPlatformStore: jest.fn(),
  isStoreDomain: jest.fn(() => false),
}));

const mockedUseAuthorizedStores = useAuthorizedStores as jest.MockedFunction<
  typeof useAuthorizedStores
>;
const mockedIsPlatformSession = isPlatformSession as jest.MockedFunction<typeof isPlatformSession>;
const mockedGetStoreId = getPlatformStoreId as jest.MockedFunction<typeof getPlatformStoreId>;
const mockedIsStoreDomain = isStoreDomain as jest.MockedFunction<typeof isStoreDomain>;

function openSwitchAndSelect(currentName: string, target: string) {
  fireEvent.click(screen.getByRole('button', { name: currentName }));
  fireEvent.click(screen.getByRole('menuitem', { name: target }));
}

describe('Header 店舗切替の常設化（#413）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/platform/dashboard';
    mockedIsPlatformSession.mockReturnValue(true);
    mockedGetStoreId.mockReturnValue('1');
    mockedUseAuthorizedStores.mockReturnValue([]);
  });

  it('授権店舗が0件なら店舗切替は表示されない', () => {
    mockedUseAuthorizedStores.mockReturnValue([]);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗の解決中（null）は店舗切替を表示しない', () => {
    mockedUseAuthorizedStores.mockReturnValue(null);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
  });

  it('授権店舗が1件なら console 値に依らず店舗切替が現在店舗名で表示される（混成束ユーザー含む）', () => {
    mockedUseAuthorizedStores.mockReturnValue([{ id: 1, name: '店舗A' }]);

    render(<Header />);

    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
  });

  it('店舗スコープページから切替すると現在地の storeId を差し替えて router.push する', () => {
    mockPathname = '/store/1/orders';
    mockedUseAuthorizedStores.mockReturnValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<Header />);
    openSwitchAndSelect('店舗A', '店舗B');

    expect(mockPush).toHaveBeenCalledWith('/store/2/orders');
  });

  it('店舗スコープ外のページから切替すると /store/{id}/dashboard へ router.push する', () => {
    mockPathname = '/platform/dashboard';
    mockedUseAuthorizedStores.mockReturnValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    render(<Header />);
    openSwitchAndSelect('店舗A', '店舗B');

    expect(mockPush).toHaveBeenCalledWith('/store/2/dashboard');
  });

  it('/platform ページで前回選択（cookie）と同じ店舗をクリックしても router.push が発火する（#413 Fix5-1）', () => {
    // pathStoreId 未確定（/platform 側）かつ cookie 前回選択が唯一の店舗と一致するケース。
    // no-op 判定を currentStoreId（cookie fallback 込み）で行うと単一店舗ユーザーが詰む。
    mockPathname = '/platform/dashboard';
    mockedGetStoreId.mockReturnValue('1');
    mockedUseAuthorizedStores.mockReturnValue([{ id: 1, name: '店舗A' }]);

    render(<Header />);
    openSwitchAndSelect('店舗A', '店舗A');

    expect(mockPush).toHaveBeenCalledWith('/store/1/dashboard');
  });

  it('店舗別ドメイン経由ではアカウント設定リンクが pathname 由来の storeId を含む店舗ルートを指す（#413 Fix2）', () => {
    mockedIsStoreDomain.mockReturnValue(true);
    mockPathname = '/store/2/dashboard';
    mockedUseAuthorizedStores.mockReturnValue([]);

    render(<Header />);

    expect(screen.getByRole('link', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/store/2/settings/account'
    );
  });

  it('店舗別ドメイン経由でも currentStoreId が未確定ならアカウント設定リンクは platform 側へ fallback する（#413 Fix3）', () => {
    mockedIsStoreDomain.mockReturnValue(true);
    mockedGetStoreId.mockReturnValue(undefined);
    mockPathname = '/store/select';
    mockedUseAuthorizedStores.mockReturnValue([]);

    render(<Header />);

    expect(screen.getByRole('link', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/platform/settings/account'
    );
  });

  it('pathname が別店舗へ変化するとラベルが新しい pathname 由来の店舗名へ追随する（#413 Fix1）', () => {
    mockedGetStoreId.mockReturnValue('2');
    mockPathname = '/store/2/dashboard';
    mockedUseAuthorizedStores.mockReturnValue([
      { id: 1, name: '店舗A' },
      { id: 2, name: '店舗B' },
    ]);

    const { rerender } = render(<Header />);

    // 初期表示は pathname 由来の store 2（店舗B）。
    expect(screen.getByRole('button', { name: '店舗B' })).toBeInTheDocument();

    // 店舗切替で URL が /store/1/... へ遷移した状態を再現する。
    mockPathname = '/store/1/dashboard';
    rerender(<Header />);

    // ラベルは古い店舗名のまま残らず、新しい pathname 由来の store 1（店舗A）へ更新される。
    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗B' })).not.toBeInTheDocument();
  });
});
