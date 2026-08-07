import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { LineLinkSection } from '../LineLinkSection';
import { platformAuthApi, platformLineApi } from '@/entities/user';
import { startLineAuthorization } from '@/shared/lib';
import type { PlatformMeResponse } from '@/entities/user';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformLineApi: { ...actual.platformLineApi, config: jest.fn() },
    platformAuthApi: { ...actual.platformAuthApi, me: jest.fn() },
  };
});

jest.mock('@/shared/lib', () => {
  const actual = jest.requireActual('@/shared/lib');
  return { ...actual, startLineAuthorization: jest.fn() };
});

const mockedConfig = platformLineApi.config as jest.Mock;
const mockedMe = platformAuthApi.me as jest.Mock;
const mockedStart = startLineAuthorization as jest.Mock;

function meResponse(lineLinked: boolean): PlatformMeResponse {
  return {
    email: 'user@kizuna.test',
    display_name: '本人',
    user_type: 'MEMBER',
    permissions: [],
    console: 'none',
    store_bridge: false,
    line_linked: lineLinked,
  };
}

describe('LineLinkSection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedConfig.mockResolvedValue({ enabled: true, channel_id: 'channel-1' });
  });

  it('未連携なら連携ボタンを表示する', async () => {
    mockedMe.mockResolvedValue(meResponse(false));

    render(<LineLinkSection />);

    expect(await screen.findByRole('button', { name: 'LINEを連携する' })).toBeInTheDocument();
    expect(screen.queryByText('連携済み')).not.toBeInTheDocument();
  });

  it('連携済みなら状態のみ表示し、解除の導線は出さない', async () => {
    mockedMe.mockResolvedValue(meResponse(true));

    render(<LineLinkSection />);

    expect(await screen.findByText('連携済み')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'LINEを連携する' })).not.toBeInTheDocument();
  });

  it('押下で発起主体つきの連携意図の認可を開始する', async () => {
    mockedMe.mockResolvedValue(meResponse(false));
    mockedStart.mockResolvedValue(undefined);

    render(<LineLinkSection />);
    fireEvent.click(await screen.findByRole('button', { name: 'LINEを連携する' }));

    await waitFor(() =>
      expect(mockedStart).toHaveBeenCalledWith('channel-1', 'link', 'user@kizuna.test')
    );
  });

  it('店舗ドメイン上（role=store cookie）ではブロックごと描画しない', async () => {
    document.cookie = 'x-mw-role=store';
    try {
      render(<LineLinkSection />);

      await waitFor(() => expect(mockedConfig).not.toHaveBeenCalled());
      expect(screen.queryByText('LINE連携')).not.toBeInTheDocument();
    } finally {
      document.cookie = 'x-mw-role=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    }
  });

  it('公開設定が無効ならブロックごと描画しない', async () => {
    mockedConfig.mockResolvedValue({ enabled: false });
    mockedMe.mockResolvedValue(meResponse(false));

    render(<LineLinkSection />);

    await waitFor(() => expect(mockedConfig).toHaveBeenCalled());
    expect(screen.queryByText('LINE連携')).not.toBeInTheDocument();
  });
});
