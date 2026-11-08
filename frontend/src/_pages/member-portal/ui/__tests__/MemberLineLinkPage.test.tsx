import type { ReactNode } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberLineLinkPage } from '../MemberLineLinkPage';
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

const me = (lineLinked: boolean) => ({ email: 'member@kizuna.test', line_linked: lineLinked });

describe('MemberLineLinkPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });
  });

  it('未連携なら状態を名乗り、押すと会員のLINE連携へ戻る連携を始める', async () => {
    mockedMe.mockResolvedValue(me(false));
    mockedStart.mockReturnValue(new Promise(() => {}));

    render(<MemberLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('未連携')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'LINEを連携する' }));

    await waitFor(() =>
      expect(mockedStart).toHaveBeenCalledWith('channel-1', {
        intent: 'link',
        subject: 'member@kizuna.test',
        returnTo: 'member-account',
      })
    );
    expect(screen.getByRole('button', { name: 'LINEへ移動中...' })).toBeDisabled();
  });

  it('連携済みなら状態だけを出し、連携の操作を出さない', async () => {
    mockedMe.mockResolvedValue(me(true));

    render(<MemberLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('連携済み')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'LINEを連携する' })).not.toBeInTheDocument();
  });

  it('LINE連携が無効なら、利用できないことだけを示す', async () => {
    mockedMe.mockResolvedValue(me(false));
    mockedConfig.mockResolvedValue({ enabled: false });

    render(<MemberLineLinkPage />, { wrapper: onHost('localhost') });

    expect(await screen.findByText('この画面からはLINE連携を利用できません。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'LINEを連携する' })).not.toBeInTheDocument();
  });

  it('取得に失敗したら区画で失敗を名乗り、再試行で連携できる', async () => {
    mockedMe.mockRejectedValueOnce(new Error('boom')).mockResolvedValue(me(false));

    render(<MemberLineLinkPage />, { wrapper: onHost('localhost') });

    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('button', { name: 'LINEを連携する' })).toBeInTheDocument();
  });
});
