import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from '../Header';
import { useMe } from '@/entities/user';
import type { MeResponse } from '@/entities/user';

// 自分の情報も同じく共有 seam（MeContext.test.tsx）が担う。ここでは context 出力に対する
// Header 自身の表示条件・ラベル・accountHref・クリックの委譲を検証する。
jest.mock('@/entities/user', () => {
  const logout = jest.fn();
  return {
    // フックを介さず参照できるよう、同じ実体をテスト向けにも公開する。
    __logout: logout,
    useAuth: () => ({ logout }),
    useMe: jest.fn(),
  };
});

const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const mockReloadMe = jest.fn();
const mockedLogout = (jest.requireMock('@/entities/user') as { __logout: jest.Mock }).__logout;

const meFixture = {
  email: 'tanaka.hanako@example.com',
  display_name: '田中花子',
  line_linked: false,
};

function withMe(me: MeResponse | null, failure: 'error' | null = null) {
  mockedUseMe.mockReturnValue({
    me,
    isLoading: me === null && failure === null,
    failure,
    reload: mockReloadMe,
    setMe: jest.fn(),
  });
}

describe('Header 操作', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    withMe(meFixture);
  });

  // アカウントメニューの中身は開いている間だけ描かれる。
  function openAccountMenu() {
    fireEvent.click(screen.getByRole('button', { name: 'アカウントメニュー' }));
  }

  it('アカウント設定リンクは常に 唯一のアカウント設定を指す', async () => {
    render(<Header />);
    openAccountMenu();

    expect(await screen.findByRole('menuitem', { name: 'アカウント設定' })).toHaveAttribute(
      'href',
      '/admin/settings/account'
    );
  });

  it('ログアウトはアカウントメニューから選べる', async () => {
    render(<Header />);
    openAccountMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'ログアウト' }));

    expect(mockedLogout).toHaveBeenCalledTimes(1);
  });

  // 切替 UI 自体の配線は ModeToggle.test.tsx が持つ。ここで押さえるのは
  // 「管理画面の全頁に共通の殻から届く」こと、つまり Header への搭載そのもの。
  it('店舗の切替は持たない（文脈の切替はサイドバーのレールが担う）', () => {
    render(<Header />);

    expect(screen.queryByText('店舗を選択')).not.toBeInTheDocument();
    expect(document.querySelector('header button[aria-haspopup]:not([aria-label])')).toBeNull();
  });

  it('表示モード切替はヘッダーから開ける', async () => {
    render(<Header />);
    fireEvent.click(screen.getByRole('button', { name: '表示モード' }));

    expect(await screen.findByRole('menuitemradio', { name: 'システム' })).toBeInTheDocument();
  });
});

// 利用者表示は /me の共有 seam から読む（display_name は token claim に無い —
// wire 契約は authorities / userType のみ）。取得・差し替えのライフサイクルは
// MeContext.test.tsx が担い、ここでは seam 出力に対する表示・失敗時の名乗り・再試行の委譲を
// 検証する。
describe('Header 利用者表示（/me の共有 seam）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    withMe(meFixture);
  });

  it('ログイン中の利用者の表示名とメールアドレスを表示する', () => {
    render(<Header />);

    expect(screen.getByText('田中花子')).toBeInTheDocument();
    expect(screen.getByText('tanaka.hanako@example.com')).toBeInTheDocument();
  });

  it('表示名とメールアドレスは上限付きで切り詰められる（行の固有幅に上界を与えるため）', () => {
    render(<Header />);

    // 表示名は 150 字まで許されるため、店舗名と同じく class 文字列を規格の錨にする。
    expect(screen.getByText('田中花子')).toHaveClass('max-w-40', 'truncate');
    expect(screen.getByText('tanaka.hanako@example.com')).toHaveClass('max-w-40', 'truncate');
  });

  it('取得に失敗した領域は自分で失敗を名乗り、再試行は seam の reload に委譲する', () => {
    withMe(null, 'error');

    render(<Header />);

    expect(screen.getByText('アカウント情報の取得に失敗しました')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(mockReloadMe).toHaveBeenCalledTimes(1);
  });
});
