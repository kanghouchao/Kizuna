import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../model/AuthContext';
import { authApi } from '../api/auth';
import { getSessionUserId, storeSessionToken } from '@/shared/api';

const mockBroadcastLogout = jest.fn();
jest.mock('@/shared/api/session', () => ({
  ...jest.requireActual('@/shared/api/session'),
  broadcastLogout: (userId: number) => mockBroadcastLogout(userId),
}));

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('../api/auth', () => ({ authApi: { logout: jest.fn() } }));

const mockClearSession = jest.fn();
jest.mock('@/shared/lib', () => ({
  // loginPath は本物を使う。差し替えると着地先の組み立てを試験が自前で持つことになり、
  // 実装がずれても緑のままになる。
  ...jest.requireActual('@/shared/lib'),
  clearSession: () => mockClearSession(),
}));

function Consumer({ reason }: { reason?: string } = {}) {
  const { logout } = useAuth();
  return (<button onClick={() => logout(reason)}>out</button>) as React.ReactElement;
}

describe('AuthProvider', () => {
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
