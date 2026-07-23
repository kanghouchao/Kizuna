import { render, screen, waitFor } from '@testing-library/react';
import { CastPortalShell } from '../CastPortalShell';
import { platformAuthApi } from '@/entities/user';
import { redirectToLogin } from '@/shared/lib';

let mockPathname = '/cast/schedule';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/entities/user', () => ({
  platformAuthApi: { me: jest.fn() },
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
}));

const mockedMe = platformAuthApi.me as jest.Mock;
const mockedRedirect = redirectToLogin as jest.Mock;

describe('CastPortalShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/cast/schedule';
  });

  it('本人確認が完了するまでローディング表示のみで、タブバーもchildrenも出さない', () => {
    mockedMe.mockReturnValue(new Promise(() => {}));

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('user_type=CAST なら children と3タブを表示する', async () => {
    mockedMe.mockResolvedValue({ user_type: 'CAST', display_name: '田中一郎' });

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
    mockedMe.mockResolvedValue({ user_type: 'CAST', display_name: '田中一郎' });

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

  it('user_type が CAST 以外ならログイン画面へ差し戻す', async () => {
    mockedMe.mockResolvedValue({ user_type: 'MEMBER', display_name: '会員太郎' });

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });

  it('本人確認 API が失敗したらログイン画面へ差し戻す', async () => {
    mockedMe.mockRejectedValue(new Error('unauthorized'));

    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await waitFor(() => expect(mockedRedirect).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('子要素')).not.toBeInTheDocument();
  });
});
