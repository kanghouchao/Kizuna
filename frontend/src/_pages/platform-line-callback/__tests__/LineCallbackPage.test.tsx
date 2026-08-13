import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import LineCallbackPage from '../ui/LineCallbackPage';
import { platformAuthApi, platformLineApi } from '@/entities/user';
import { completePlatformLogin } from '@/features/platform-login';

const mockPush = jest.fn();
const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformAuthApi: { ...actual.platformAuthApi, me: jest.fn() },
    platformLineApi: {
      config: jest.fn(),
      login: jest.fn(),
      register: jest.fn(),
      link: jest.fn(),
    },
  };
});

jest.mock('@/features/platform-login', () => ({
  completePlatformLogin: jest.fn(),
}));

const mockedLineApi = platformLineApi as jest.Mocked<typeof platformLineApi>;
const mockedMe = platformAuthApi.me as jest.Mock;
const mockedComplete = completePlatformLogin as jest.MockedFunction<typeof completePlatformLogin>;

const STORAGE_KEY = 'kizuna-line-oauth';
const REDIRECT_URI = 'http://localhost/platform/line/callback';

function arriveAt(
  query: string,
  stored?: { state: string; verifier: string; intent: string; subject?: string }
) {
  if (stored) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  }
  window.history.replaceState({}, '', `/platform/line/callback${query}`);
}

describe('LineCallbackPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
  });

  afterEach(() => {
    Cookies.remove('token');
    Cookies.remove('platform-role');
    Cookies.remove('member-return-path');
  });

  describe('ログイン意図', () => {
    it('登録済みならセッションを確立して着地先へ遷移する', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: true,
        token: 'jwt-token',
        expires_at: Date.now() + 3_600_000,
      });
      mockedComplete.mockResolvedValue({ status: 'ok', path: '/member/' });

      render(<LineCallbackPage />);

      await waitFor(() =>
        expect(mockedLineApi.login).toHaveBeenCalledWith({
          code: 'auth-code',
          redirect_uri: REDIRECT_URI,
          code_verifier: 'v1',
        })
      );
      // 消費済みのコールバック URL を履歴に残さないため replace で遷移する
      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/member/'));
      // 保存値は一度きりの消費
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('未登録なら会員登録の確認段階を同じ画面に表示する', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: false,
        registration_ticket: 'ticket-1',
        display_name: 'LINE太郎',
      });

      render(<LineCallbackPage />);

      expect(await screen.findByLabelText('表示名')).toHaveValue('LINE太郎');
      expect(screen.getByLabelText('メールアドレス')).toBeInTheDocument();
      expect(mockPush).not.toHaveBeenCalled();
    });

    it('登録は登録チケットと入力値を送り、会員セッションで会員ポータルへ遷移する', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: false,
        registration_ticket: 'ticket-1',
        display_name: 'LINE太郎',
      });
      mockedLineApi.register.mockResolvedValue({
        token: 'jwt-token',
        expires_at: Date.now() + 3_600_000,
      });

      render(<LineCallbackPage />);

      fireEvent.change(await screen.findByLabelText('メールアドレス'), {
        target: { value: 'member@kizuna.test' },
      });
      fireEvent.click(screen.getByLabelText('利用規約およびプライバシーポリシーに同意します'));
      fireEvent.click(screen.getByRole('button', { name: '登録する' }));

      await waitFor(() =>
        expect(mockedLineApi.register).toHaveBeenCalledWith({
          registration_ticket: 'ticket-1',
          display_name: 'LINE太郎',
          email: 'member@kizuna.test',
        })
      );
      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/member/'));
      expect(Cookies.get('token')).toBe('jwt-token');
      expect(Cookies.get('platform-role')).toBe('member');
    });

    it('伝票の QR から未登録で来ていた場合、登録後は申領画面へ戻ること', async () => {
      // 預かった戻り先を消費しないと、その人は伝票を取り込めないまま、断片は次に同じタブで
      // ログインした別の会員へ渡ってしまう
      Cookies.set('member-return-path', '/member/receipts');
      sessionStorage.setItem('member-return-fragment', '#tok3n');
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: false,
        registration_ticket: 'ticket-1',
        display_name: 'LINE太郎',
      });
      mockedLineApi.register.mockResolvedValue({
        token: 'jwt-token',
        expires_at: Date.now() + 3_600_000,
      });

      render(<LineCallbackPage />);

      fireEvent.change(await screen.findByLabelText('メールアドレス'), {
        target: { value: 'member@kizuna.test' },
      });
      fireEvent.click(screen.getByLabelText('利用規約およびプライバシーポリシーに同意します'));
      fireEvent.click(screen.getByRole('button', { name: '登録する' }));

      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/member/receipts#tok3n'));
      expect(Cookies.get('member-return-path')).toBeUndefined();
      expect(sessionStorage.getItem('member-return-fragment')).toBeNull();
    });

    it('同意チェックが無ければ登録を送らない', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: false,
        registration_ticket: 'ticket-1',
        display_name: 'LINE太郎',
      });

      render(<LineCallbackPage />);

      fireEvent.change(await screen.findByLabelText('メールアドレス'), {
        target: { value: 'member@kizuna.test' },
      });
      fireEvent.click(screen.getByRole('button', { name: '登録する' }));

      expect(await screen.findByText('同意いただける場合のみ登録できます')).toBeInTheDocument();
      expect(mockedLineApi.register).not.toHaveBeenCalled();
    });

    it('着地先の無い利用者種別はエラー表示にする', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockResolvedValue({
        registered: true,
        token: 'jwt-token',
        expires_at: Date.now() + 3_600_000,
      });
      mockedComplete.mockResolvedValue({ status: 'unsupported' });

      render(<LineCallbackPage />);

      expect(await screen.findByText('この利用者種別のポータルは準備中です')).toBeInTheDocument();
      expect(mockPush).not.toHaveBeenCalled();
    });
  });

  describe('連携意図', () => {
    it('発起主体と現在のログインが一致すれば連携を要求し、アカウント設定へ戻す', async () => {
      arriveAt('?code=auth-code&state=s2', {
        state: 's2',
        verifier: 'v2',
        intent: 'link',
        subject: 'me@kizuna.test',
      });
      mockedMe.mockResolvedValue({ email: 'me@kizuna.test' });
      mockedLineApi.link.mockResolvedValue(undefined);

      render(<LineCallbackPage />);

      await waitFor(() =>
        expect(mockedLineApi.link).toHaveBeenCalledWith({
          code: 'auth-code',
          redirect_uri: REDIRECT_URI,
          code_verifier: 'v2',
        })
      );
      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/platform/settings/account'));
      expect(mockedLineApi.login).not.toHaveBeenCalled();
    });

    it('外部認可の往復中に別アカウントへ切り替わっていたら連携を送らない', async () => {
      arriveAt('?code=auth-code&state=s2', {
        state: 's2',
        verifier: 'v2',
        intent: 'link',
        subject: 'me@kizuna.test',
      });
      mockedMe.mockResolvedValue({ email: 'other@kizuna.test' });

      render(<LineCallbackPage />);

      expect(
        await screen.findByText(
          'LINE連携を開始したアカウントと現在のログインが一致しません。アカウント設定からやり直してください'
        )
      ).toBeInTheDocument();
      expect(mockedLineApi.link).not.toHaveBeenCalled();
    });

    it('409 はサーバーの文言を表示し、アカウント設定への戻り導線を示す', async () => {
      arriveAt('?code=auth-code&state=s2', {
        state: 's2',
        verifier: 'v2',
        intent: 'link',
        subject: 'me@kizuna.test',
      });
      mockedMe.mockResolvedValue({ email: 'me@kizuna.test' });
      // 「本人が連携済み」と「他の身分が連携済み」はサーバーが文言で区別する
      mockedLineApi.link.mockRejectedValue({
        response: { status: 409, data: { error: 'このアカウントは既に LINE と連携済みです' } },
      });

      render(<LineCallbackPage />);

      expect(
        await screen.findByText('このアカウントは既に LINE と連携済みです')
      ).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'アカウント設定へ戻る' })).toHaveAttribute(
        'href',
        '/platform/settings/account'
      );
      expect(mockReplace).not.toHaveBeenCalled();
    });
  });

  describe('異常系', () => {
    it('state が食い違う場合はエラー表示にし、引き換えを試みない', async () => {
      arriveAt('?code=auth-code&state=別のstate', {
        state: 's1',
        verifier: 'v1',
        intent: 'login',
      });

      render(<LineCallbackPage />);

      expect(
        await screen.findByText(
          '認証を確認できませんでした。お手数ですが最初からやり直してください'
        )
      ).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'ログイン画面へ戻る' })).toHaveAttribute(
        'href',
        '/platform/login'
      );
      expect(mockedLineApi.login).not.toHaveBeenCalled();
    });

    it('保存が無い場合（別タブでの開始など）もエラー表示にする', async () => {
      arriveAt('?code=auth-code&state=s1');

      render(<LineCallbackPage />);

      expect(
        await screen.findByText(
          '認証を確認できませんでした。お手数ですが最初からやり直してください'
        )
      ).toBeInTheDocument();
      expect(mockedLineApi.login).not.toHaveBeenCalled();
    });

    it('LINE 側のエラー（利用者による中止）はエラー表示にする', async () => {
      arriveAt('?error=access_denied&error_description=User+denied&state=s1', {
        state: 's1',
        verifier: 'v1',
        intent: 'login',
      });

      render(<LineCallbackPage />);

      expect(await screen.findByText('LINEでの認証が完了しませんでした')).toBeInTheDocument();
      expect(mockedLineApi.login).not.toHaveBeenCalled();
    });

    it('引き換えの失敗はサーバーの文言を表示する', async () => {
      arriveAt('?code=auth-code&state=s1', { state: 's1', verifier: 'v1', intent: 'login' });
      mockedLineApi.login.mockRejectedValue({
        response: { data: { error: '認可コードが無効です' } },
      });

      render(<LineCallbackPage />);

      expect(await screen.findByText('認可コードが無効です')).toBeInTheDocument();
    });
  });
});
