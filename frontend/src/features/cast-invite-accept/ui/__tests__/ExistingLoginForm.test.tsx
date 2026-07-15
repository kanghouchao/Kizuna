import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Cookies from 'js-cookie';
import { ExistingLoginForm } from '../ExistingLoginForm';
import { castInvitationAcceptanceApi } from '@/entities/cast';
import { platformAuthApi } from '@/entities/user';
import {
  getPlatformRole,
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

  it('ログイン成功時、新しい token を設定する前に旧セッションの platform-role / platform-store-id を消去する', async () => {
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

    expect(getPlatformRole()).toBeUndefined();
    expect(getPlatformStoreId()).toBeUndefined();
    expect(Cookies.get('token')).toBe('cast-jwt');
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
    expect(getPlatformRole()).toBe('STORE_MANAGER');
    expect(getPlatformStoreId()).toBe('7');
  });
});
