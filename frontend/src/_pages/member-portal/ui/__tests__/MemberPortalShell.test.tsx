import { render, screen } from '@testing-library/react';
import { MemberPortalShell } from '../MemberPortalShell';
import { redirectToLogin, rememberMemberReturnPath } from '@/shared/lib';
import { useMe, type MeResponse } from '@/entities/user';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
  rememberMemberReturnPath: jest.fn(),
}));

jest.mock('@/entities/user/model/MeContext', () => ({
  MeProvider: ({ children }: { children: React.ReactNode }) => children,
  useMe: jest.fn(),
}));

const mockedUseMe = useMe as jest.Mock;

function member(overrides: Partial<MeResponse>): MeResponse {
  return {
    email: 'member@kizuna.test',
    display_name: '会員',
    user_type: 'MEMBER',
    permissions: [],
    line_linked: false,
    email_verified: false,
    ...overrides,
  };
}

function renderShell(me: MeResponse | null) {
  mockedUseMe.mockReturnValue({
    me,
    isLoading: me === null,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  render(
    <MemberPortalShell>
      <p>子要素</p>
    </MemberPortalShell>
  );
}

describe('MemberPortalShell', () => {
  // 未ログインの差し戻しと戻り先の預けは全域の 401 経路が担い、シェル自身は本人を判定しない
  it('セッションを判定せず children を表示する', () => {
    renderShell(null);

    expect(screen.getByText('子要素')).toBeInTheDocument();
    expect(redirectToLogin).not.toHaveBeenCalled();
    expect(rememberMemberReturnPath).not.toHaveBeenCalled();
  });

  it('未検証の会員には確認メールを送った宛先つきの案内を内容の前に出す', () => {
    renderShell(member({ email_verified: false }));

    const banner = screen.getByRole('status');
    expect(banner).toHaveTextContent('メールアドレスの確認が済んでいません');
    expect(banner).toHaveTextContent('member@kizuna.testに確認メールを送りました');
    expect(
      banner.compareDocumentPosition(screen.getByText('子要素')) & Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it('検証済みの会員には案内を出さない', () => {
    renderShell(member({ email_verified: true }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('本人資料を読めるまでは案内を出さない', () => {
    renderShell(null);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('会員以外のセッションには案内を出さない', () => {
    renderShell(member({ user_type: 'STAFF', email_verified: false }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
