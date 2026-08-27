import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { StaffAccountSummaryResponse, platformStaffAccountApi } from '@/entities/user';
import { notify } from '@/shared/notify';
import StaffAccountsPage from '../ui/StaffAccountsPage';

jest.mock('@/entities/user', () => ({
  platformStaffAccountApi: { list: jest.fn(), suspend: jest.fn(), resume: jest.fn() },
}));

jest.mock('@/features/staff-management', () => ({
  roleSetLabel: (roles: { name?: string }[] | undefined) =>
    roles && roles.length > 0 ? roles.map(role => role.name).join('・') : '未選択',
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = platformStaffAccountApi as jest.Mocked<typeof platformStaffAccountApi>;

const account = (override: Partial<StaffAccountSummaryResponse>): StaffAccountSummaryResponse => ({
  id: 1,
  email: 'staff@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [{ id: 3, name: '本部管理者' }],
  ...override,
});

const paginated = (
  rows: StaffAccountSummaryResponse[],
  override: Partial<PageResult<StaffAccountSummaryResponse>> = {}
): PageResult<StaffAccountSummaryResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

describe('アカウント管理ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.suspend.mockResolvedValue(undefined);
    mockedApi.resume.mockResolvedValue(undefined);
    mockedApi.list.mockResolvedValue(
      paginated([
        account({ id: 1, display_name: '山田太郎', email: 'yamada@example.com', enabled: true }),
        account({
          id: 2,
          display_name: '鈴木花子',
          email: 'suzuki@example.com',
          enabled: false,
          roles: [{ id: 4, name: '受付担当' }],
        }),
      ])
    );
  });

  it('表示名・メールアドレス・ロール・状態を一覧表示すること', async () => {
    render(<StaffAccountsPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.getByText('yamada@example.com')).toBeInTheDocument();
    expect(screen.getByText('本部管理者')).toBeInTheDocument();
    expect(screen.getByText('受付担当')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
  });

  // この面は授権を何も付与しない。編集への導線が生えると設計そのものが崩れる。
  it('授権を編集する導線を持たないこと', async () => {
    render(<StaffAccountsPage />);
    await screen.findByText('山田太郎');

    expect(screen.queryByRole('button', { name: '編集' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('有効な行には停止だけ、停止中の行には再開だけを置くこと', async () => {
    render(<StaffAccountsPage />);
    await screen.findByText('山田太郎');

    expect(screen.getAllByRole('button', { name: '停止' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: '再開' })).toHaveLength(1);
  });

  // 停止は対象のセッションを即時に失効させるため、確認を経てからでないと実行しない
  it('停止は確認してから実行し、成功後に一覧を取り直すこと', async () => {
    render(<StaffAccountsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));

    expect(await screen.findByText('アカウントを停止しますか？')).toBeInTheDocument();
    expect(mockedApi.suspend).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '停止する' }));

    await waitFor(() => expect(mockedApi.suspend).toHaveBeenCalledWith(1));
    expect(notify.success).toHaveBeenCalledWith('アカウントを停止しました');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('確認をキャンセルすると停止しないこと', async () => {
    render(<StaffAccountsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));
    await screen.findByText('アカウントを停止しますか？');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    await waitFor(() =>
      expect(screen.queryByText('アカウントを停止しますか？')).not.toBeInTheDocument()
    );
    expect(mockedApi.suspend).not.toHaveBeenCalled();
  });

  // 拒否の理由（自分自身・最後の管理権限保持者）はサーバだけが持つので、そのまま出す
  it('停止の拒否はサーバの文言をそのまま通知すること', async () => {
    mockedApi.suspend.mockRejectedValue({
      response: { data: { error: '最後の管理権限保持者を停止・降格することはできません' } },
    });

    render(<StaffAccountsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));
    fireEvent.click(await screen.findByRole('button', { name: '停止する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        '最後の管理権限保持者を停止・降格することはできません'
      )
    );
    expect(notify.success).not.toHaveBeenCalled();
  });

  // 再開は元に戻す操作なので確認を挟まない
  it('再開は確認なしで実行し、成功後に一覧を取り直すこと', async () => {
    render(<StaffAccountsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再開' }));

    await waitFor(() => expect(mockedApi.resume).toHaveBeenCalledWith(2));
    expect(notify.success).toHaveBeenCalledWith('アカウントを再開しました');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  it('再開の失敗はサーバの文言をそのまま通知すること', async () => {
    mockedApi.resume.mockRejectedValue({
      response: { data: { error: 'アカウントが見つかりません: 2' } },
    });

    render(<StaffAccountsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再開' }));

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('アカウントが見つかりません: 2'));
  });

  it('検索は 0 起点の page/size/search のペイロードで再取得すること', async () => {
    render(<StaffAccountsPage />);
    await screen.findByText('山田太郎');

    fireEvent.change(screen.getByLabelText('アカウントを検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith({ page: 0, size: 10, search: '山田' })
    );
  });

  it('クリアは検索語を空にして取り直すこと', async () => {
    render(<StaffAccountsPage />);
    await screen.findByText('山田太郎');
    fireEvent.change(screen.getByLabelText('アカウントを検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ search: '山田' }))
    );

    fireEvent.click(screen.getByRole('button', { name: 'クリア' }));

    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith({ page: 0, size: 10, search: undefined })
    );
    expect(screen.getByLabelText('アカウントを検索')).toHaveValue('');
  });

  it('ページ番号のクリックで該当ページを取得すること', async () => {
    mockedApi.list.mockImplementation(({ page }) =>
      Promise.resolve(
        paginated([account({ id: 1, display_name: '山田太郎' })], { page, pageCount: 3, total: 25 })
      )
    );

    render(<StaffAccountsPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith({ page: 1, size: 10, search: undefined })
    );
  });

  it('取得に失敗した一覧は自分で名乗り、再試行を置くこと', async () => {
    mockedApi.list.mockRejectedValueOnce(new Error('network'));

    render(<StaffAccountsPage />);

    expect(await screen.findByText('アカウント一覧の取得に失敗しました')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
  });
});

describe('アカウント管理ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.list.mockResolvedValue(paginated([]));
  });

  it('見出し（h1）・副題を備えること', async () => {
    render(<StaffAccountsPage />);
    await screen.findByText('アカウントが登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: 'アカウント管理' })).toBeInTheDocument();
    expect(
      screen.getByText('スタッフアカウントの状態を確認し、停止・再開します。')
    ).toBeInTheDocument();
  });
});
