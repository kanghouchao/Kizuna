import { notify } from '@/shared/notify';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { ExistingLoginForm } from '../ExistingLoginForm';
import { castInvitationAcceptanceApi } from '@/entities/cast';
import { authApi } from '@/entities/user';
import { getSessionKind, getCurrentStoreId, setCurrentStore, startSession } from '@/shared/lib';

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castInvitationAcceptanceApi: {
      ...actual.castInvitationAcceptanceApi,
      acceptAsExistingUser: jest.fn(),
    },
  };
});

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      login: jest.fn(),
    },
  };
});

const mockedAcceptanceApi = castInvitationAcceptanceApi as jest.Mocked<
  typeof castInvitationAcceptanceApi
>;
const mockedAuthApi = authApi as jest.Mocked<typeof authApi>;

function submitForm(password = 'pass') {
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'cast@kizuna.test' },
  });
  fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));
}

function expectPreviousSessionIntact() {
  expect(Cookies.get('token')).toBe('previous-token');
  expect(getSessionKind()).toBe('admin');
  expect(getCurrentStoreId()).toBe('7');
}

describe('ExistingLoginForm', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 招待を開く前に別のアカウントでログイン済みだった状況を再現する
    Cookies.set('token', 'previous-token');
    startSession('admin');
    setCurrentStore('7');
  });

  afterEach(() => {
    Cookies.remove('session-kind');
    Cookies.remove('current-store-id');
    Cookies.remove('token');
  });

  it('資格情報と招待トークンを一回の受諾要求で送り、ログインは呼ばない', async () => {
    mockedAcceptanceApi.acceptAsExistingUser.mockResolvedValue({ store_name: '渋谷店' });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);
    submitForm();

    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ store_name: '渋谷店' }));
    expect(mockedAcceptanceApi.acceptAsExistingUser).toHaveBeenCalledTimes(1);
    expect(mockedAcceptanceApi.acceptAsExistingUser).toHaveBeenCalledWith('tok-1', {
      email: 'cast@kizuna.test',
      password: 'pass',
    });
    expect(mockedAuthApi.login).not.toHaveBeenCalled();
  });

  it('受諾に成功しても、訪問者の元のセッションをそのまま残す', async () => {
    mockedAcceptanceApi.acceptAsExistingUser.mockResolvedValue({ store_name: '渋谷店' });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);
    submitForm();

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expectPreviousSessionIntact();
  });

  it('資格情報の誤り(401)では理由を通知し、元のセッションを残す', async () => {
    const error = jest.spyOn(notify, 'error').mockReturnValue('notification-id');
    mockedAcceptanceApi.acceptAsExistingUser.mockRejectedValue({
      response: {
        status: 401,
        data: { error: 'メールアドレスまたはパスワードが正しくありません' },
      },
    });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);
    submitForm('wrong');

    await waitFor(() =>
      expect(error).toHaveBeenCalledWith('メールアドレスまたはパスワードが正しくありません')
    );
    expect(onSuccess).not.toHaveBeenCalled();
    expectPreviousSessionIntact();
    error.mockRestore();
  });

  it('同店の重複受諾409では理由を通知して入力と元のセッションを維持する', async () => {
    const error = jest.spyOn(notify, 'error').mockReturnValue('notification-id');
    mockedAcceptanceApi.acceptAsExistingUser.mockRejectedValue({
      response: { status: 409, data: { message: 'この店舗には既に有効な在籍があります' } },
    });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="invite-token" onSuccess={onSuccess} onBack={jest.fn()} />);
    submitForm('input-value');

    await waitFor(() => expect(error).toHaveBeenCalledWith('この店舗には既に有効な在籍があります'));
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('cast@kizuna.test');
    expect(screen.getByRole('button', { name: 'ログインして受諾する' })).toBeEnabled();
    expect(onSuccess).not.toHaveBeenCalled();
    expectPreviousSessionIntact();
    error.mockRestore();
  });
});
