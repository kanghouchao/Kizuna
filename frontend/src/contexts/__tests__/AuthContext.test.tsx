import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '@/contexts/AuthContext';
import Cookies from 'js-cookie';

import { authApi } from '@/services/central/api';
import { isTenantDomain } from '@/lib/config';

jest.mock('js-cookie');

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ push: mockPush }) }));

jest.mock('@/services/central/api', () => ({
  authApi: { logout: jest.fn() },
}));

jest.mock('@/lib/config', () => ({
  isTenantDomain: jest.fn(),
}));

function Consumer() {
  const { logout } = useAuth();
  return (<button onClick={() => logout()}>out</button>) as React.ReactElement;
}

describe('AuthProvider', () => {
  afterEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockReset?.();
    (isTenantDomain as jest.Mock).mockReset?.();
  });

  it('logout calls api and removes token and navigates', async () => {
    (isTenantDomain as jest.Mock).mockReturnValue(false);
    (Cookies.get as jest.Mock).mockReturnValue('tkn');
    (authApi.logout as jest.Mock).mockResolvedValueOnce({});
    const removeSpy = jest.spyOn(Cookies, 'remove');
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    getByText('out').click();
    await waitFor(() => expect(authApi.logout).toHaveBeenCalled());
    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  it('provides logout function via context', () => {
    (isTenantDomain as jest.Mock).mockReturnValue(false);
    const { getByText } = render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    expect(getByText('out')).toBeInTheDocument();
  });

  it('throws error when useAuth is used outside AuthProvider', () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Consumer />)).toThrow(
      'useAuth must be used within an AuthProvider'
    );
    consoleError.mockRestore();
  });
});
