import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CastAccountPage } from '../CastAccountPage';
import { platformAuthApi, useAuth } from '@/entities/user';

jest.mock('@/entities/user', () => ({
  platformAuthApi: { me: jest.fn() },
  useAuth: jest.fn(),
}));

const mockedMe = platformAuthApi.me as jest.Mock;
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

  it('ログアウトボタンは useAuth().logout を呼ぶ', async () => {
    mockedMe.mockResolvedValue({ display_name: '田中一郎' });

    render(<CastAccountPage />);
    await screen.findByText('田中一郎');
    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));

    await waitFor(() => expect(mockLogout).toHaveBeenCalledTimes(1));
  });
});
