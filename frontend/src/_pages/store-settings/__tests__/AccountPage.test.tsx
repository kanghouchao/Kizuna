import { StrictMode } from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
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

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
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
    expect(notify.error).not.toHaveBeenCalled();

    mockedAuthApi.me.mockResolvedValue(me as never);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByLabelText('ニックネーム *')).toHaveValue('店長太郎');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が欄をクリアする
  // 以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  it('二重 mount で古い失敗が後から着いても、新しい成功を消さないこと', async () => {
    let failStale = (): void => {};
    mockedAuthApi.me
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue(me as never);

    render(
      <StrictMode>
        <AccountPage />
      </StrictMode>
    );

    expect(await screen.findByLabelText('ニックネーム *')).toHaveValue('店長太郎');

    await act(async () => {
      failStale();
    });

    expect(screen.getByLabelText('ニックネーム *')).toHaveValue('店長太郎');
    expect(screen.queryByText('アカウント情報の取得に失敗しました')).not.toBeInTheDocument();
  });

  // 上の 1 本は成功・catch の比較しか固定しない（どちらの飛行も着いた後で観測するため、在途の
  // setIsLoading(false) は既に false の旗へ落ちる）。finally の比較は 2 度目を在途のまま留める
  // この形でしか赤にならない
  it('二度目が在途のまま古い失敗が着いても、読み込み表示を畳まないこと', async () => {
    let failStale = (): void => {};
    mockedAuthApi.me
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockReturnValueOnce(new Promise(() => {}));

    render(
      <StrictMode>
        <AccountPage />
      </StrictMode>
    );

    await act(async () => {
      failStale();
    });

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('取得に失敗してもパスワード変更は使えたままであること', async () => {
    mockedAuthApi.me.mockRejectedValueOnce(new Error('boom'));

    render(<AccountPage />);

    await screen.findByRole('alert');
    expect(screen.getByRole('button', { name: 'パスワードを変更する' })).toBeEnabled();
  });
});
