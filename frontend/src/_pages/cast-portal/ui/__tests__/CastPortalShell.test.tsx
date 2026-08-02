import { render, screen, waitFor } from '@testing-library/react';
import { CastPortalShell } from '../CastPortalShell';
import { readTokenClaims, redirectToLogin } from '@/shared/lib';

let mockPathname = '/cast/schedule';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
  redirectToLogin: jest.fn(),
}));

const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;
const mockedRedirect = redirectToLogin as jest.Mock;

const castClaims = { authorities: ['ROLE_CAST'], userType: 'CAST', storeBridge: false };

describe('CastPortalShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/cast/schedule';
  });

  it('userType=CAST なら children と3タブを表示する', async () => {
    mockedReadClaims.mockReturnValue(castClaims);

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    expect(await screen.findByText('子要素')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /スケジュール/ })).toHaveAttribute(
      'href',
      '/cast/schedule'
    );
    expect(screen.getByRole('link', { name: /希望提出/ })).toHaveAttribute(
      'href',
      '/cast/requests'
    );
    expect(screen.getByRole('link', { name: /アカウント/ })).toHaveAttribute(
      'href',
      '/cast/account'
    );
    expect(mockedRedirect).not.toHaveBeenCalled();
  });

  it('現在のパスに一致するタブを aria-current=page でハイライトする', async () => {
    mockPathname = '/cast/account';
    mockedReadClaims.mockReturnValue(castClaims);

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await screen.findByText('子要素');

    expect(screen.getByRole('link', { name: /アカウント/ })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('link', { name: /スケジュール/ })).not.toHaveAttribute('aria-current');
  });

  it('userType が CAST 以外ならログイン画面へ差し戻す', async () => {
    mockedReadClaims.mockReturnValue({
      authorities: ['ROLE_MEMBER'],
      userType: 'MEMBER',
      storeBridge: false,
    });

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('token が無い・壊れている（claims=null）ならログイン画面へ差し戻す', async () => {
    mockedReadClaims.mockReturnValue(null);

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });
});
