import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CastAccountPage } from '../CastAccountPage';
import { platformAuthApi, platformLineApi, useAuth } from '@/entities/user';

jest.mock('@/entities/user', () => ({
  platformAuthApi: { me: jest.fn() },
  platformLineApi: { config: jest.fn() },
  useAuth: jest.fn(),
}));

const mockedMe = platformAuthApi.me as jest.Mock;
const mockedLineConfig = platformLineApi.config as jest.Mock;
const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockLogout = jest.fn();

describe('CastAccountPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedUseAuth.mockReturnValue({ logout: mockLogout });
    mockedLineConfig.mockResolvedValue({ enabled: false, channel_id: null });
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

  it('LINE 連携が有効なら連携ブロックを表示する', async () => {
    mockedMe.mockResolvedValue({ display_name: '田中一郎', line_linked: false });
    mockedLineConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });

    render(<CastAccountPage />);

    expect(await screen.findByRole('heading', { name: 'LINE連携' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'LINEを連携する' })).toBeInTheDocument();
  });
});
