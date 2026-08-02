import { render, screen, waitFor } from '@testing-library/react';
import { MemberPortalShell } from '../MemberPortalShell';
import { readTokenClaims, redirectToLogin } from '@/shared/lib';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
  redirectToLogin: jest.fn(),
}));

const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;
const mockedRedirect = redirectToLogin as jest.Mock;

describe('MemberPortalShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('userType=MEMBER なら children を表示する', async () => {
    mockedReadClaims.mockReturnValue({
      authorities: ['ROLE_MEMBER'],
      userType: 'MEMBER',
      storeBridge: false,
    });

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    expect(await screen.findByText('子要素')).toBeInTheDocument();
    expect(mockedRedirect).not.toHaveBeenCalled();
  });

  it('userType が MEMBER 以外ならログイン画面へ差し戻す', async () => {
    mockedReadClaims.mockReturnValue({
      authorities: ['ROLE_CAST'],
      userType: 'CAST',
      storeBridge: false,
    });

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('token が無い・壊れている（claims=null）ならログイン画面へ差し戻す', async () => {
    mockedReadClaims.mockReturnValue(null);

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });
});
