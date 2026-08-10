import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import AccountPage from '../ui/AccountPage';
import { MeProvider, platformAuthApi } from '@/entities/user';

// AccountPage は /platform/me の共有 seam（MeProvider）越しに自分の情報を読む。seam は
// 実物を張って取得・再試行・差し替えの配線ごと検証し、その先の API だけを差し替える
// （MeContext 内部の相対 import も同じモジュールへ解決されるため、この mock が効く）。
jest.mock('@/entities/user/api/platform', () => ({
  platformAuthApi: {
    me: jest.fn(),
    updateMe: jest.fn(),
    changePassword: jest.fn(),
  },
}));
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useAuth: () => ({ logout: jest.fn() }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

const me = { display_name: '店長太郎', email: 'tencho@example.com' };

function renderPage() {
  return render(
    <MeProvider>
      <AccountPage />
    </MeProvider>
  );
}

describe('店舗アカウント設定の取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('取得に失敗したら空欄のフォームを出さず、区画が失敗を名乗って再試行できること', async () => {
    mockedAuthApi.me.mockRejectedValueOnce(new Error('boom'));

    renderPage();

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

  it('取得に失敗してもパスワード変更は使えたままであること', async () => {
    mockedAuthApi.me.mockRejectedValueOnce(new Error('boom'));

    renderPage();

    await screen.findByRole('alert');
    expect(screen.getByRole('button', { name: 'パスワードを変更する' })).toBeEnabled();
  });

  it('保存できた表示名がそのまま欄に残ること', async () => {
    // 保存の応答を欄の起点に据え直す。取り直しに倒すと、直した欄が読み込み表示で一瞬消える。
    // me は毎回新しい参照を返す — 取り直しに倒した実装なら、ここで古い表示名が返って赤になる
    mockedAuthApi.me.mockImplementation(async () => ({ ...me }) as never);
    mockedAuthApi.updateMe.mockResolvedValue({ ...me, display_name: '新しい名前' } as never);

    renderPage();
    const nickname = await screen.findByLabelText('ニックネーム *');

    fireEvent.change(nickname, { target: { value: '新しい名前' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(notify.success).toHaveBeenCalledWith('プロフィールを更新しました'));
    expect(mockedAuthApi.updateMe).toHaveBeenCalledWith({ display_name: '新しい名前' });
    expect(screen.getByLabelText('ニックネーム *')).toHaveValue('新しい名前');
  });
});
