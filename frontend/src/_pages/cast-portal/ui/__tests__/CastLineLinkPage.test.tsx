import type { ReactNode } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastLineLinkPage } from '../CastLineLinkPage';
import { authApi, lineApi } from '@/entities/user';
import { AppDomainProvider, startLineAuthorization } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
jest.mock('@/entities/user', () => ({
  authApi: { me: jest.fn() },
  lineApi: { config: jest.fn() },
}));
jest.mock('@/shared/lib', () => {
  const actual = jest.requireActual('@/shared/lib');
  return { ...actual, startLineAuthorization: jest.fn() };
});

const mockedMe = authApi.me as jest.Mock;
const mockedConfig = lineApi.config as jest.Mock;
const mockedStart = startLineAuthorization as jest.Mock;

// jsdom の location.hostname は localhost。アプリドメインと一致させるとアプリの画面として描く
function onHost(appDomain: string) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <AppDomainProvider appDomain={appDomain}>{children}</AppDomainProvider>;
  };
}

const me = (lineLinked: boolean) => ({
  email: 'cast@kizuna.test',
  display_name: '田中',
  line_linked: lineLinked,
});

describe('CastLineLinkPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });
  });

  it('未連携なら状態を名乗り、押すとキャストのLINE連携へ戻る連携を始める', async () => {
    mockedMe.mockResolvedValue(me(false));

    render(<CastLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('未連携')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'LINEを連携する' }));

    await waitFor(() =>
      expect(mockedStart).toHaveBeenCalledWith('channel-1', {
        intent: 'link',
        subject: 'cast@kizuna.test',
        returnTo: 'cast-account',
      })
    );
  });

  it('連携済みなら状態だけを出し、連携の操作を出さない', async () => {
    mockedMe.mockResolvedValue(me(true));

    render(<CastLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('連携済み')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'LINEを連携する' })).not.toBeInTheDocument();
  });

  it('公開設定が無効なら利用できないことを名乗り、連携の操作を出さない', async () => {
    mockedConfig.mockResolvedValue({ enabled: false, channel_id: null });
    mockedMe.mockResolvedValue(me(false));

    render(<CastLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('この画面からはLINE連携を利用できません。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'LINEを連携する' })).not.toBeInTheDocument();
  });

  it('店舗ドメイン上では設定を取りに行かず、利用できないことを名乗る', async () => {
    render(<CastLineLinkPage />, { wrapper: onHost('app.example') });

    expect(await screen.findByText('この画面からはLINE連携を利用できません。')).toBeInTheDocument();
    expect(mockedConfig).not.toHaveBeenCalled();
  });

  it('状態を取れなければ失敗を名乗り、再試行で読み直せる', async () => {
    mockedMe.mockRejectedValueOnce(new Error('boom'));

    render(<CastLineLinkPage />, { wrapper: onHost('localhost') });

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('LINE連携の状態を取得できませんでした')).toBeInTheDocument();

    mockedMe.mockResolvedValue(me(true));
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('連携済み')).toBeInTheDocument();
  });
});
