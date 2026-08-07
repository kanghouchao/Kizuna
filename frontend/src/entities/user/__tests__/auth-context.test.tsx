import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../model/AuthContext';
import Cookies from 'js-cookie';

import { platformAuthApi } from '../api/platform';

jest.mock('js-cookie');

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('../api/platform', () => ({ platformAuthApi: { logout: jest.fn() } }));

const mockClearPlatformSession = jest.fn();
jest.mock('@/shared/lib', () => ({
  // loginPath は本物を使う。差し替えると着地先の組み立てを試験が自前で持つことになり、
  // 実装がずれても緑のままになる。
  ...jest.requireActual('@/shared/lib'),
  clearPlatformSession: () => mockClearPlatformSession(),
}));

function Consumer({ reason }: { reason?: string } = {}) {
  const { logout } = useAuth();
  return (<button onClick={() => logout(reason)}>out</button>) as React.ReactElement;
}

describe('AuthProvider', () => {
  afterEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockReset?.();
  });

  it('logout calls api and removes token and navigates', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('tkn');
    (platformAuthApi.logout as jest.Mock).mockResolvedValueOnce({});
    const removeSpy = jest.spyOn(Cookies, 'remove');
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() => expect(platformAuthApi.logout).toHaveBeenCalled());
    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(mockClearPlatformSession).toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith('/platform/login');
  });

  // 利用者が自分で押したログアウトが、身に覚えのない説明の書かれた画面に着地しないこと。
  // 理由は呼び出し側が明示したときだけ載る。
  it('理由を渡したときだけログイン画面へ理由コードを載せる', async () => {
    (platformAuthApi.logout as jest.Mock).mockResolvedValueOnce({});
    const { getByText } = render(
      <AuthProvider>
        <Consumer reason="password-changed" />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() =>
      expect(mockPush).toHaveBeenCalledWith('/platform/login?reason=password-changed')
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
