import { fireEvent, render, screen, within } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import AccountPage from '../ui/AccountPage';
import { platformAuthApi } from '@/entities/user';

jest.mock('@/entities/user', () => ({
  platformAuthApi: {
    me: jest.fn(),
    updateMe: jest.fn(),
    changePassword: jest.fn(),
  },
  useAuth: () => ({ logout: jest.fn() }),
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

const me = { display_name: '店長太郎', email: 'tencho@example.com' };

describe('店舗アカウント設定の取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('取得に失敗したら空欄のフォームを出さず、区画が失敗を名乗って再試行できること', async () => {
    mockedAuthApi.me.mockRejectedValueOnce(new Error('boom'));

    render(<AccountPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('アカウント情報の取得に失敗しました')).toBeInTheDocument();
    // 空欄のまま保存できると、ニックネームが空文字で上書きされる
    expect(screen.queryByLabelText('ニックネーム *')).not.toBeInTheDocument();
    expect(toast.error).not.toHaveBeenCalled();

    mockedAuthApi.me.mockResolvedValue(me as never);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByLabelText('ニックネーム *')).toHaveValue('店長太郎');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('取得に失敗してもパスワード変更は使えたままであること', async () => {
    mockedAuthApi.me.mockRejectedValueOnce(new Error('boom'));

    render(<AccountPage />);

    await screen.findByRole('alert');
    expect(screen.getByRole('button', { name: 'パスワードを変更する' })).toBeEnabled();
  });
});
