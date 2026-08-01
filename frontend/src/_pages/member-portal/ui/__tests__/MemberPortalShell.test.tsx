import { render, screen, waitFor } from '@testing-library/react';
import { MemberPortalShell } from '../MemberPortalShell';
import { platformAuthApi } from '@/entities/user';
import { redirectToLogin } from '@/shared/lib';

jest.mock('@/entities/user', () => ({
  platformAuthApi: { me: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
}));

const mockedMe = platformAuthApi.me as jest.Mock;
const mockedRedirect = redirectToLogin as jest.Mock;

describe('MemberPortalShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('本人確認が完了するまでローディング表示のみで、childrenを出さない', () => {
    mockedMe.mockReturnValue(new Promise(() => {}));

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('user_type=MEMBER なら children を表示する', async () => {
    mockedMe.mockResolvedValue({ user_type: 'MEMBER', display_name: '会員花子' });

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    expect(await screen.findByText('子要素')).toBeInTheDocument();
    expect(mockedRedirect).not.toHaveBeenCalled();
  });

  it('user_type が MEMBER 以外ならログイン画面へ差し戻す', async () => {
    mockedMe.mockResolvedValue({ user_type: 'CAST', display_name: 'キャスト太郎' });

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('本人確認 API が失敗したらログイン画面へ差し戻す', async () => {
    mockedMe.mockRejectedValue(new Error('unauthorized'));

    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });
});
