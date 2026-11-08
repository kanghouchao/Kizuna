import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { CastAccountPage } from '../CastAccountPage';
import { authApi, useAuth } from '@/entities/user';

jest.mock('@/entities/user', () => ({
  authApi: { me: jest.fn() },
  useAuth: jest.fn(),
}));

const mockedMe = authApi.me as jest.Mock;
const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockLogout = jest.fn();

describe('CastAccountPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedUseAuth.mockReturnValue({ logout: mockLogout });
  });

  it('表示名を取得して表示する', async () => {
    mockedMe.mockResolvedValue({ display_name: '田中一郎' });

    render(<CastAccountPage />);

    expect(await screen.findByText('田中一郎')).toBeInTheDocument();
  });

  it('サービス条件・パスワード変更・LINE連携の下位画面へ入れる', async () => {
    mockedMe.mockResolvedValue({ display_name: '田中一郎' });

    render(<CastAccountPage />);

    const settings = screen.getByRole('navigation', { name: '設定' });
    expect(within(settings).getByRole('link', { name: 'サービス条件' })).toHaveAttribute(
      'href',
      '/cast/service-conditions'
    );
    expect(within(settings).getByRole('link', { name: 'パスワード変更' })).toHaveAttribute(
      'href',
      '/cast/account/password'
    );
    expect(within(settings).getByRole('link', { name: 'LINE連携' })).toHaveAttribute(
      'href',
      '/cast/account/line'
    );
    await screen.findByText('田中一郎');
  });

  it('ログアウトボタンは useAuth().logout を呼ぶ', async () => {
    mockedMe.mockResolvedValue({ display_name: '田中一郎' });

    render(<CastAccountPage />);
    await screen.findByText('田中一郎');
    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));

    await waitFor(() => expect(mockLogout).toHaveBeenCalledTimes(1));
  });

  it('表示名が取れなければ読み込み表示に固着せず、失敗を名乗って再試行できる', async () => {
    mockedMe.mockRejectedValueOnce(new Error('boom'));

    render(<CastAccountPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('表示名を取得できませんでした')).toBeInTheDocument();
    expect(screen.queryByRole('status', { name: '読み込み中' })).not.toBeInTheDocument();

    mockedMe.mockResolvedValue({ display_name: '田中一郎' });
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('田中一郎')).toBeInTheDocument();
  });
});
