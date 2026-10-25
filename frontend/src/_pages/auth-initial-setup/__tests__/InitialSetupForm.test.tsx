import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import { authApi, initialSetupApi } from '@/entities/user';
import { completeLogin } from '@/features/login';
import { InitialSetupForm } from '../ui/InitialSetupForm';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    authApi: { ...actual.authApi, login: jest.fn() },
    initialSetupApi: { register: jest.fn() },
  };
});

jest.mock('@/features/login', () => ({
  completeLogin: jest.fn(),
}));

const mockedRegister = initialSetupApi.register as jest.Mock;
const mockedLogin = authApi.login as jest.Mock;
const mockedComplete = completeLogin as jest.MockedFunction<typeof completeLogin>;
const mockedNotifyError = notify.error as jest.Mock;
const mockedNotifySuccess = notify.success as jest.Mock;

const LOGIN_RESPONSE = { token: 'jwt-token', expires_at: Date.now() + 3_600_000 };

function fillAndSubmit(confirmPassword = 'password1234') {
  render(<InitialSetupForm />);
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'admin@kizuna.test' },
  });
  fireEvent.change(screen.getByLabelText('表示名'), { target: { value: '管理者' } });
  fireEvent.change(screen.getByLabelText('パスワード（8文字以上）'), {
    target: { value: 'password1234' },
  });
  fireEvent.change(screen.getByLabelText('パスワード（確認）'), {
    target: { value: confirmPassword },
  });
  fireEvent.click(screen.getByRole('button', { name: '登録する' }));
}

describe('InitialSetupForm', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    Cookies.remove('token');
  });

  it('確認欄がパスワードと一致しなければ送信せず、欄の傍に理由を出す', async () => {
    fillAndSubmit('password9999');

    expect(await screen.findByText('パスワードが一致しません')).toBeInTheDocument();
    expect(screen.getByLabelText('パスワード（確認）')).toHaveAttribute('aria-invalid', 'true');
    expect(mockedRegister).not.toHaveBeenCalled();
  });

  it('登録後にそのままログインし、着地先へ遷移する（確認欄は送らない）', async () => {
    // 旧 DB のトークンが残っていても、登録とログインには持ち込まない
    Cookies.set('token', 'stale-token');
    let tokenAtRegister: string | undefined = 'not-called';
    mockedRegister.mockImplementation(async () => {
      tokenAtRegister = Cookies.get('token');
    });
    mockedLogin.mockResolvedValue(LOGIN_RESPONSE);
    mockedComplete.mockResolvedValue({ status: 'ok', path: '/admin/dashboard/' });

    fillAndSubmit();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/dashboard/'));
    expect(tokenAtRegister).toBeUndefined();
    expect(mockedRegister).toHaveBeenCalledWith({
      email: 'admin@kizuna.test',
      password: 'password1234',
      display_name: '管理者',
    });
    expect(mockedLogin).toHaveBeenCalledWith(
      { email: 'admin@kizuna.test', password: 'password1234' },
      { skipAuthRedirect: true }
    );
    expect(mockedComplete).toHaveBeenCalledWith(LOGIN_RESPONSE);
    expect(mockedNotifyError).not.toHaveBeenCalled();
    expect(mockedNotifySuccess).not.toHaveBeenCalled();
  });

  it('既に完了済み（404）ならその旨を伝えてログイン画面へ送る', async () => {
    mockedRegister.mockRejectedValue({ response: { status: 404, data: { error: 'Not Found' } } });

    fillAndSubmit();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/auth/login'));
    expect(mockedNotifyError).toHaveBeenCalledWith('初期設定は完了しています');
    expect(mockedLogin).not.toHaveBeenCalled();
  });

  it('その他の失敗はサーバーの文言で知らせ、画面に留まる', async () => {
    mockedRegister.mockRejectedValue({
      response: { status: 400, data: { error: 'このメールアドレスは既に使用されています' } },
    });

    fillAndSubmit();

    await waitFor(() =>
      expect(mockedNotifyError).toHaveBeenCalledWith('このメールアドレスは既に使用されています')
    );
    expect(mockedLogin).not.toHaveBeenCalled();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it.each([
    ['自動ログインが失敗した', () => mockedLogin.mockRejectedValue(new Error('login failed'))],
    [
      '着地先の無い利用者種別だった',
      () => {
        mockedLogin.mockResolvedValue(LOGIN_RESPONSE);
        mockedComplete.mockResolvedValue({ status: 'unsupported' });
      },
    ],
  ])('登録は済んだが%s場合はログイン画面へ誘導する', async (_label, arrange) => {
    mockedRegister.mockResolvedValue(undefined);
    arrange();

    fillAndSubmit();

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/auth/login'));
    // 登録自体は完了しているので成功の段。行き先は同じ root layout なので通知は生き残る。
    expect(mockedNotifySuccess).toHaveBeenCalledWith('登録が完了しました。ログインしてください');
    expect(mockedNotifyError).not.toHaveBeenCalled();
  });
});
