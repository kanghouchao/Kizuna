import type { ReactNode } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { LineLoginButton } from '../LineLoginButton';
import { lineApi } from '@/entities/user';
import { AppDomainProvider, startLineAuthorization } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    lineApi: { ...actual.lineApi, config: jest.fn() },
  };
});

jest.mock('@/shared/lib', () => {
  const actual = jest.requireActual('@/shared/lib');
  return { ...actual, startLineAuthorization: jest.fn() };
});

const mockedConfig = lineApi.config as jest.Mock;
const mockedStart = startLineAuthorization as jest.Mock;

// jsdom の location.hostname は localhost。アプリドメインと一致させるとアプリの画面として描く
function onHost(appDomain: string) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <AppDomainProvider appDomain={appDomain}>{children}</AppDomainProvider>;
  };
}

describe('LineLoginButton', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('公開設定が有効なときだけ入口を描画する', async () => {
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });

    render(<LineLoginButton />, { wrapper: onHost('localhost') });

    expect(await screen.findByRole('button', { name: 'LINEでログイン' })).toBeInTheDocument();
  });

  it('店舗ドメイン上（アプリドメインと異なるホスト）では設定を照会せず何も描画しない', async () => {
    render(<LineLoginButton />, { wrapper: onHost('app.example') });

    await waitFor(() => expect(mockedConfig).not.toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('公開設定が無効なら何も描画しない', async () => {
    mockedConfig.mockResolvedValue({ enabled: false });

    render(<LineLoginButton />, { wrapper: onHost('localhost') });

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('チャネル未設定なら何も描画しない', async () => {
    mockedConfig.mockResolvedValue({ enabled: true });

    render(<LineLoginButton />, { wrapper: onHost('localhost') });

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('設定の取得に失敗しても何も描画しない（パスワードログインは無傷）', async () => {
    mockedConfig.mockRejectedValue(new Error('network'));

    render(<LineLoginButton />, { wrapper: onHost('localhost') });

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'LINEでログイン' })).not.toBeInTheDocument();
  });

  it('押下でログイン意図の認可を開始する', async () => {
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });
    mockedStart.mockResolvedValue(undefined);

    render(<LineLoginButton />, { wrapper: onHost('localhost') });
    fireEvent.click(await screen.findByRole('button', { name: 'LINEでログイン' }));

    await waitFor(() => expect(mockedStart).toHaveBeenCalledWith('channel-1', 'login'));
  });
});
