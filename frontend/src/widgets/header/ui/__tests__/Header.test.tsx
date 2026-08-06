import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from '../Header';
import { useStoreContext } from '@/entities/user';
import { isStoreDomain } from '@/shared/lib';
import type { PlatformStore } from '@/entities/user';

// 現在店舗・授権店舗・切替は店舗コンテキストが担い、その全ケースは StoreContext.test.tsx で検証する。
// ここでは context 出力（stores / currentStoreId）に対する Header 自身の表示条件・ラベル・
// accountHref・切替クリックの委譲を検証する。
jest.mock('@/entities/user', () => {
  const logout = jest.fn();
  return {
    // フックを介さず参照できるよう、同じ実体をテスト向けにも公開する。
    __logout: logout,
    useAuth: () => ({ logout }),
    useStoreContext: jest.fn(),
  };
});

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  isStoreDomain: jest.fn(() => false),
}));

const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedIsStoreDomain = isStoreDomain as jest.MockedFunction<typeof isStoreDomain>;
const mockSwitchStore = jest.fn();
const mockedLogout = (jest.requireMock('@/entities/user') as { __logout: jest.Mock }).__logout;

function withContext(stores: PlatformStore[] | null, currentStoreId?: string) {
  mockedUseStoreContext.mockReturnValue({
    stores,
    storeBridge: stores === null ? null : stores.length > 0,
    currentStoreId,
    loadFailed: false,
    reload: jest.fn(),
    switchStore: mockSwitchStore,
  });
}

describe('Header 店舗切替の常設化（店舗コンテキスト集約）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedIsStoreDomain.mockReturnValue(false);
    withContext([]);
  });

  it('授権店舗が0件なら店舗切替は表示されない', () => {
    withContext([]);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗A' })).not.toBeInTheDocument();
  });

  it('授権店舗の解決中（null）は店舗切替を表示しない', () => {
    withContext(null);

    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
  });

  it('授権店舗が1件なら現在店舗名で店舗切替が表示される', () => {
    withContext([{ id: 1, name: '店舗A' }], '1');

    render(<Header />);

    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
  });

  it('店舗切替クリックは context の switchStore に店舗 id を委譲する', async () => {
    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '1'
    );

    render(<Header />);
    // トリガーは click で開く。jsdom の fireEvent.click は
    // pointerdown を合成しないため、キーボードでメニューを開いてから項目を選ぶ。
    fireEvent.click(screen.getByRole('button', { name: '店舗A' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '店舗B' }));

    expect(mockSwitchStore).toHaveBeenCalledWith(2);
  });

  // アカウントメニューの中身は開いている間だけ描かれる。
  function openAccountMenu() {
    fireEvent.click(screen.getByRole('button', { name: 'アカウントメニュー' }));
  }

  it('店舗別ドメイン経由ではアカウント設定リンクが currentStoreId を含む店舗ルートを指す', async () => {
    mockedIsStoreDomain.mockReturnValue(true);
    withContext([], '2');

    render(<Header />);
    openAccountMenu();

    expect(await screen.findByRole('menuitem', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/store/2/settings/account'
    );
  });

  it('店舗別ドメイン経由でも currentStoreId が未確定ならアカウント設定リンクは platform 側へ fallback する', async () => {
    mockedIsStoreDomain.mockReturnValue(true);
    withContext([], undefined);

    render(<Header />);
    openAccountMenu();

    expect(await screen.findByRole('menuitem', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/platform/settings/account'
    );
  });

  it('ログアウトはアカウントメニューから選べる', async () => {
    withContext([], '1');

    render(<Header />);
    openAccountMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'ログアウト' }));

    expect(mockedLogout).toHaveBeenCalledTimes(1);
  });

  // 切替 UI 自体の配線は ModeToggle.test.tsx が持つ。ここで押さえるのは
  // 「両コンソールの共通殻から届く」こと、つまり Header への搭載そのもの。
  it('表示モード切替はヘッダーから開ける', async () => {
    withContext([], '1');

    render(<Header />);
    fireEvent.click(screen.getByRole('button', { name: '表示モード' }));

    expect(await screen.findByRole('menuitemradio', { name: 'システム' })).toBeInTheDocument();
  });

  it('currentStoreId が変化するとラベルが新しい店舗名へ追随する', () => {
    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '2'
    );

    const { rerender } = render(<Header />);

    expect(screen.getByRole('button', { name: '店舗B' })).toBeInTheDocument();

    withContext(
      [
        { id: 1, name: '店舗A' },
        { id: 2, name: '店舗B' },
      ],
      '1'
    );
    rerender(<Header />);

    expect(screen.getByRole('button', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '店舗B' })).not.toBeInTheDocument();
  });

  it('店舗名は上限付きで切り詰められる（行の固有幅に上界を与えるため）', () => {
    withContext([{ id: 1, name: '店舗A' }], '1');

    render(<Header />);

    // 店名は 200 字まで許されるため、上限が無いとシェルの下限幅では守れなくなる。
    // 幅は jsdom では測れないので、規格として定めた class 文字列そのものを錨にする。
    expect(screen.getByText('店舗A')).toHaveClass('max-w-40', 'truncate');
  });
});
