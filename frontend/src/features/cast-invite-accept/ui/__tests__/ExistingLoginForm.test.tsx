import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { ExistingLoginForm } from '../ExistingLoginForm';
import { castInvitationAcceptanceApi } from '@/entities/cast';
import { platformAuthApi } from '@/entities/user';
import {
  getPlatformConsole,
  getPlatformStoreId,
  setPlatformStore,
  startPlatformSession,
} from '@/shared/lib';

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
    platformAuthApi: {
      ...actual.platformAuthApi,
      login: jest.fn(),
    },
  };
});

const mockedAcceptanceApi = castInvitationAcceptanceApi as jest.Mocked<
  typeof castInvitationAcceptanceApi
>;
const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

describe('ExistingLoginForm 旧プラットフォームセッションのcookie消去（#327、codex指摘対応）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 招待を開く前に別ロールで平台にログイン済みだった状況を再現する
    startPlatformSession('STORE_MANAGER');
    setPlatformStore('7');
  });

  afterEach(() => {
    Cookies.remove('platform-role');
    Cookies.remove('platform-store-id');
    Cookies.remove('token');
  });

  it('platformAuthApi.login を skipAuthRedirect: true で呼び、招待ログインの401をグローバルセッション処理から除外する（#327 codex指摘）', async () => {
    mockedAuthApi.login.mockResolvedValue({
      token: 'cast-jwt',
      expires_at: Date.now() + 60 * 60 * 1000,
    });
    mockedAcceptanceApi.acceptAsExistingUser.mockResolvedValue({ store_name: '渋谷店' });

    render(<ExistingLoginForm token="tok-1" onSuccess={jest.fn()} onBack={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    await waitFor(() => expect(mockedAuthApi.login).toHaveBeenCalledTimes(1));

    expect(mockedAuthApi.login).toHaveBeenCalledWith(
      { email: 'cast@kizuna.test', password: 'pass' },
      { skipAuthRedirect: true }
    );
  });

  it('ログイン成功時、新しい token を設定する前に旧セッションの platform-role / platform-store-id を消去する', async () => {
    let tokenDuringAcceptance: string | undefined;
    mockedAuthApi.login.mockResolvedValue({
      token: 'cast-jwt',
      expires_at: Date.now() + 60 * 60 * 1000,
    });
    mockedAcceptanceApi.acceptAsExistingUser.mockImplementation(async () => {
      // 受諾 API 呼び出し時点では一時 CAST token が設定されている必要がある
      tokenDuringAcceptance = Cookies.get('token');
      return { store_name: '渋谷店' };
    });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));

    expect(getPlatformConsole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
    expect(tokenDuringAcceptance).toBe('cast-jwt');
  });

  it('受諾成功後、一時的に設定した CAST token を消去する（#327 codex指摘: token だけが残ると central/tenant へ誤誘導される）', async () => {
    mockedAuthApi.login.mockResolvedValue({
      token: 'cast-jwt',
      expires_at: Date.now() + 60 * 60 * 1000,
    });
    mockedAcceptanceApi.acceptAsExistingUser.mockResolvedValue({ store_name: '渋谷店' });
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));

    expect(Cookies.get('token')).toBeUndefined();
    expect(getPlatformConsole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
  });

  it('ログイン自体が失敗した場合、新 token を書き込んでいないので既存の token cookie を消去しない', async () => {
    Cookies.set('token', 'existing-jwt');
    mockedAuthApi.login.mockRejectedValue(new Error('invalid credentials'));

    render(<ExistingLoginForm token="tok-1" onSuccess={jest.fn()} onBack={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    await waitFor(() => expect(mockedAuthApi.login).toHaveBeenCalledTimes(1));

    expect(mockedAcceptanceApi.acceptAsExistingUser).not.toHaveBeenCalled();
    expect(Cookies.get('token')).toBe('existing-jwt');
    expect(getPlatformConsole()).toBe('STORE_MANAGER');
    expect(getPlatformStoreId()).toBe('7');
  });

  it('ログイン成功後に受諾APIが失敗した場合、消去済みの旧セッション（platform-role/platform-store-id/token）を復元する（#327 codex指摘: でないと訪問者がログアウト状態に落ちる）', async () => {
    Cookies.set('token', 'existing-jwt');
    mockedAuthApi.login.mockResolvedValue({
      token: 'cast-jwt',
      expires_at: Date.now() + 60 * 60 * 1000,
    });
    mockedAcceptanceApi.acceptAsExistingUser.mockRejectedValue(new Error('invite expired'));
    const onSuccess = jest.fn();

    render(<ExistingLoginForm token="tok-1" onSuccess={onSuccess} onBack={jest.fn()} />);

    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    // 'cast-jwt' はログイン成功直後に一時的に張られる token。catch 節の復元処理が完了して
    // 'existing-jwt' に戻るまで待つ（acceptAsExistingUser 呼び出しだけでは復元完了を保証しないため）
    await waitFor(() => {
      expect(mockedAcceptanceApi.acceptAsExistingUser).toHaveBeenCalledTimes(1);
      expect(Cookies.get('token')).not.toBe('cast-jwt');
    });

    expect(onSuccess).not.toHaveBeenCalled();
    expect(Cookies.get('token')).toBe('existing-jwt');
    expect(getPlatformConsole()).toBe('STORE_MANAGER');
    expect(getPlatformStoreId()).toBe('7');
  });
});
