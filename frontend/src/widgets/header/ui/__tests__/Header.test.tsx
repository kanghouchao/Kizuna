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
    platformAuthApi: { me: jest.fn() },
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
const mockedMe = (jest.requireMock('@/entities/user') as { platformAuthApi: { me: jest.Mock } })
  .platformAuthApi.me;

const meFixture = {
  email: 'tanaka.hanako@example.com',
  display_name: '田中花子',
  store_bridge: false,
  line_linked: false,
};

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
    mockedMe.mockResolvedValue(meFixture);
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

// 利用者表示は /platform/me から取る（display_name は token claim に無い — wire 契約は
// authorities / userType / storeBridge のみ）。ここでは取得結果の表示・失敗時の名乗り・
// 再試行の配線を検証する。
describe('Header 利用者表示（/platform/me）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedIsStoreDomain.mockReturnValue(false);
    mockedMe.mockResolvedValue(meFixture);
    withContext([]);
  });

  it('ログイン中の利用者の表示名とメールアドレスを表示する', async () => {
    render(<Header />);

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(screen.getByText('tanaka.hanako@example.com')).toBeInTheDocument();
  });

  it('表示名とメールアドレスは上限付きで切り詰められる（行の固有幅に上界を与えるため）', async () => {
    render(<Header />);

    // 表示名は 150 字まで許されるため、店舗名と同じく class 文字列を規格の錨にする。
    expect(await screen.findByText('田中花子')).toHaveClass('max-w-40', 'truncate');
    expect(screen.getByText('tanaka.hanako@example.com')).toHaveClass('max-w-40', 'truncate');
  });

  it('取得に失敗した領域は自分で失敗を名乗り、再試行で取り直せる', async () => {
    mockedMe.mockReset();
    mockedMe.mockRejectedValueOnce(new Error('network'));
    mockedMe.mockResolvedValue(meFixture);

    render(<Header />);

    expect(await screen.findByText('アカウント情報の取得に失敗しました')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(screen.queryByText('アカウント情報の取得に失敗しました')).not.toBeInTheDocument();
  });
});
