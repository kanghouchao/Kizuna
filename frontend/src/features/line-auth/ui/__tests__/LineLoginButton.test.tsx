import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { LineLoginButton } from '../LineLoginButton';
import { platformLineApi } from '@/entities/user';
import { startLineAuthorization } from '@/shared/lib';

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { error: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformLineApi: { ...actual.platformLineApi, config: jest.fn() },
  };
});

jest.mock('@/shared/lib', () => {
  const actual = jest.requireActual('@/shared/lib');
  return { ...actual, startLineAuthorization: jest.fn() };
});

const mockedConfig = platformLineApi.config as jest.Mock;
const mockedStart = startLineAuthorization as jest.Mock;

describe('LineLoginButton', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('公開設定が有効なときだけ入口を描画する', async () => {
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });

    render(<LineLoginButton />);

    expect(await screen.findByRole('button', { name: 'LINEでログイン' })).toBeInTheDocument();
  });

  it('店舗ドメイン上（role=store cookie）では設定を照会せず何も描画しない', async () => {
    document.cookie = 'x-mw-role=store';
    try {
      render(<LineLoginButton />);

      await waitFor(() => expect(mockedConfig).not.toHaveBeenCalled());
      expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
    } finally {
      document.cookie = 'x-mw-role=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    }
  });

  it('公開設定が無効なら何も描画しない', async () => {
    mockedConfig.mockResolvedValue({ enabled: false });

    render(<LineLoginButton />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('チャネル未設定なら何も描画しない', async () => {
    mockedConfig.mockResolvedValue({ enabled: true });

    render(<LineLoginButton />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('設定の取得に失敗しても何も描画しない（パスワードログインは無傷）', async () => {
    mockedConfig.mockRejectedValue(new Error('network'));

    render(<LineLoginButton />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('押下でログイン意図の認可を開始する', async () => {
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });
    mockedStart.mockResolvedValue(undefined);

    render(<LineLoginButton />);
    fireEvent.click(await screen.findByRole('button', { name: 'LINEでログイン' }));

    await waitFor(() => expect(mockedStart).toHaveBeenCalledWith('channel-1', 'login'));
  });
});
