import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../model/AuthContext';
import { authApi } from '../api/auth';
import { endSession, getSessionUserId, storeSessionToken } from '@/shared/api';

// 他タブへの通知はセッションモジュールの内側で送られるので、送信路（BroadcastChannel）で受け取る。
const mockBroadcastLogout = jest.fn();
class FakeBroadcastChannel {
  postMessage(message: { user_id: number }) {
    mockBroadcastLogout(message.user_id);
  }
  close() {}
}

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('../api/auth', () => ({ authApi: { logout: jest.fn() } }));

const mockNotifyError = jest.fn();
jest.mock('@/shared/notify', () => ({
  notify: { error: (message: string) => mockNotifyError(message) },
}));

const mockClearSession = jest.fn();
jest.mock('@/shared/lib/session', () => ({
  ...jest.requireActual('@/shared/lib/session'),
  clearSession: () => mockClearSession(),
}));

function Consumer({ reason }: { reason?: string } = {}) {
  const { logout } = useAuth();
  return (<button onClick={() => logout(reason)}>out</button>) as React.ReactElement;
}

describe('AuthProvider', () => {
  const originalChannel = globalThis.BroadcastChannel;
  beforeAll(() => {
    globalThis.BroadcastChannel = FakeBroadcastChannel as unknown as typeof BroadcastChannel;
  });
  afterAll(() => {
    globalThis.BroadcastChannel = originalChannel;
  });
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('ログアウトは端点を呼んでからメモリのトークンとセッションを消し、同じ利用者の他のタブへ知らせてログインへ移る', async () => {
    storeSessionToken({ token: 'tkn', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
    let tokenAtLogout: number | undefined;
    (authApi.logout as jest.Mock).mockImplementationOnce(async () => {
      // 端点は Bearer から失効させるセッションを取るので、消すのは呼んだ後
      tokenAtLogout = getSessionUserId();
    });
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() => expect(authApi.logout).toHaveBeenCalled());
    expect(tokenAtLogout).toBe(7);
    expect(getSessionUserId()).toBeUndefined();
    expect(mockClearSession).toHaveBeenCalled();
    expect(mockBroadcastLogout).toHaveBeenCalledWith(7);
    expect(mockPush).toHaveBeenCalledWith('/auth/login');
  });

  it('端点を待つ間に別の要求の 401 がトークンを消しても、呼ぶ前の利用者 ID で他のタブへ知らせる', async () => {
    storeSessionToken({ token: 'tkn', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
    (authApi.logout as jest.Mock).mockImplementationOnce(async () => {
      // 端点がセッションを失効させた直後、並行する要求の 401 が差し戻しでトークンを消す
      endSession('expired');
    });
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/auth/login'));
    expect(mockBroadcastLogout).toHaveBeenCalledWith(7);
  });

  it('ログアウト要求が通信の失敗で届かなければ、セッションを残して知らせ、ログイン画面へは移らない', async () => {
    // リフレッシュ cookie が残ったまま移ると、ログイン画面の自動着地で元のセッションへ戻ってしまう。
    storeSessionToken({ token: 'tkn', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
    (authApi.logout as jest.Mock).mockRejectedValueOnce(new Error('Network Error'));
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() => expect(mockNotifyError).toHaveBeenCalled());
    expect(getSessionUserId()).toBe(7);
    expect(mockClearSession).not.toHaveBeenCalled();
    expect(mockBroadcastLogout).not.toHaveBeenCalled();
    expect(mockPush).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  // 利用者が自分で押したログアウトが、身に覚えのない説明の書かれた画面に着地しないこと。
  // 理由は呼び出し側が明示したときだけ載る。
  it('理由を渡したときだけログイン画面へ理由コードを載せる', async () => {
    (authApi.logout as jest.Mock).mockResolvedValueOnce({});
    const { getByText } = render(
      <AuthProvider>
        <Consumer reason="password-changed" />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith('/auth/login?reason=password-changed')
    );
  });

  it('provides logout function via context', () => {
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    expect(getByText('out')).toBeInTheDocument();
  });

  it('throws error when useAuth is used outside AuthProvider', () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Consumer />)).toThrow('useAuth must be used within an AuthProvider');
    consoleError.mockRestore();
  });
});
