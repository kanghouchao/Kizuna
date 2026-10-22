import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { StaffResponse, staffApi, useStoreContext } from '@/entities/user';
import { TokenClaims, readTokenClaims } from '@/shared/lib';
import { notify } from '@/shared/notify';
import StaffPage from '../ui/StaffPage';

jest.mock('@/entities/user', () => ({
  staffApi: {
    list: jest.fn(),
    get: jest.fn(),
    suspend: jest.fn(),
    resume: jest.fn(),
    resetPassword: jest.fn(),
  },
  useStoreContext: jest.fn(),
}));

// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

// モーダルは開くまで mount されないため、mock は mount = 表示として描画する
jest.mock('@/features/staff-management', () => {
  const React = require('react');
  return {
    StaffCreateModal: () => React.createElement('div', null, '作成モーダル表示中'),
    StaffEditModal: ({
      resource,
      onUpdated,
    }: {
      resource: import('@/shared/lib').KeyedResource<StaffResponse>;
      onUpdated: () => void;
    }) =>
      React.createElement(
        'div',
        null,
        `編集モーダル:${resource.data?.display_name ?? '読み込み中'}`,
        // 409 で本体が呼ぶ一覧再取得を、テストから起こせるようにする
        React.createElement(
          'button',
          {
            onClick: () => {
              onUpdated();
              void resource.reload();
            },
          },
          '競合再取得'
        )
      ),
    roleSetLabel: () => 'ロールラベル',
  };
});

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedStaffApi = staffApi as jest.Mocked<typeof staffApi>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;

const claimsWith = (...permissions: string[]) =>
  ({
    authorities: permissions.map(permission => `PERM_${permission}`),
    userType: 'STAFF',
  }) as TokenClaims;

/** 店舗コンテキストの授権店舗を差し替える。reload は再試行導線の検証に使う。 */
const givenStores = (
  stores: { id: number; name: string }[] | null,
  override: { loadFailed?: boolean; reload?: () => void } = {}
) => {
  const reload = override.reload ?? jest.fn();
  mockedStoreContext.mockReturnValue({
    stores,
    currentStoreId: undefined,
    loadFailed: override.loadFailed ?? false,
    reload,
    switchStore: jest.fn(),
  } as unknown as ReturnType<typeof useStoreContext>);
  return reload;
};

const staff = (override: Partial<StaffResponse>): StaffResponse => ({
  id: 1,
  email: 'staff@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [],
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  version: 0,
  editable: true,
  ...override,
});

const paginated = (
  rows: StaffResponse[],
  override: Partial<PageResult<StaffResponse>> = {}
): PageResult<StaffResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

/**
 * 店舗の絞り込みを選ぶ。項目の確定には pointerdown が要るため、
 * キーボードで開いて項目をクリックする（store-shifts のセレクト操作に倣う）。
 */
async function pickStore(optionName: string) {
  fireEvent.click(screen.getByRole('combobox', { name: '店舗で絞り込む' }));
  const option = await screen.findByRole('option', { name: optionName });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

describe('従業員管理ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedStaffApi.get.mockImplementation(async id =>
      staff({ id, display_name: id === 2 ? '鈴木花子' : '山田太郎' })
    );
    givenStores([]);
    mockedReadClaims.mockReturnValue(claimsWith('STAFF_MANAGE'));
    mockedStaffApi.list.mockResolvedValue(
      paginated([
        staff({ id: 1, display_name: '山田太郎', email: 'yamada@example.com', enabled: true }),
        staff({ id: 2, display_name: '鈴木花子', email: 'suzuki@example.com', enabled: false }),
      ])
    );
  });

  it('氏名・ログインメールアドレス・在籍状態を一覧表示すること', async () => {
    render(<StaffPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('鈴木花子')).toBeInTheDocument();
    expect(screen.getByText('yamada@example.com')).toBeInTheDocument();
    expect(screen.getByText('suzuki@example.com')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
  });

  // 編集の導線は行内のボタンのみ（行クリックは廃止。マウス専用の導線を残さない）
  it('行内の編集ボタンで対象従業員の編集モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[0]);

    expect(await screen.findByText('編集モーダル:山田太郎')).toBeInTheDocument();
  });

  it('行そのものをクリックしても編集モーダルは開かないこと', async () => {
    render(<StaffPage />);

    fireEvent.click(await screen.findByText('鈴木花子'));

    expect(screen.queryByText('編集モーダル:鈴木花子')).not.toBeInTheDocument();
  });

  it('従業員を追加ボタンで作成モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: '従業員を追加' }));

    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });

  // 店舗の選択肢は店舗コンテキストの授権店舗をそのまま使い、ページで取り直さない
  it('店舗コンテキストの授権店舗を絞り込みの候補に出すこと', async () => {
    givenStores([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    fireEvent.click(screen.getByRole('combobox', { name: '店舗で絞り込む' }));

    expect(await screen.findByRole('option', { name: '店舗A' })).toBeInTheDocument();
  });

  it('店舗目録の取得失敗は選択肢の領域で名乗り、再試行で店舗コンテキストを取り直すこと', async () => {
    const reload = givenStores(null, { loadFailed: true });

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    expect(screen.getByRole('alert')).toHaveTextContent('店舗一覧の取得に失敗しました');
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    expect(reload).toHaveBeenCalledTimes(1);
  });

  // 409 の再取得は「最新の内容を確認してください」と言うための導線。対象が現在ページから
  // 外れても（本人の改名で検索から外れる・他の追加で次ページへずれる）モーダルは閉じず、
  // 版が古いまま再試行が 409 を繰り返さないよう id で最新値を取り直す。
  it('再取得で対象が現在ページから外れても、id で最新値を取り直してモーダルを開いたままにすること', async () => {
    mockedStaffApi.get
      .mockResolvedValueOnce(staff({ id: 2, display_name: '鈴木花子' }))
      .mockResolvedValue(staff({ id: 2, display_name: '鈴木花子（改名後）', version: 3 }));

    render(<StaffPage />);
    await screen.findByText('鈴木花子');
    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[1]);
    expect(await screen.findByText('編集モーダル:鈴木花子')).toBeInTheDocument();

    // 再取得後の頁には対象が居ない
    mockedStaffApi.list.mockResolvedValue(paginated([staff({ id: 1, display_name: '山田太郎' })]));
    fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));

    expect(await screen.findByText('編集モーダル:鈴木花子（改名後）')).toBeInTheDocument();
    expect(mockedStaffApi.get).toHaveBeenCalledWith(2);
  });

  it('対象を取り直せなくてもモーダルは閉じないこと', async () => {
    mockedStaffApi.get.mockRejectedValue(new Error('not found'));

    render(<StaffPage />);
    await screen.findByText('鈴木花子');
    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[1]);

    mockedStaffApi.list.mockResolvedValue(paginated([staff({ id: 1, display_name: '山田太郎' })]));
    fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));

    await waitFor(() => expect(mockedStaffApi.get).toHaveBeenCalledWith(2));
    expect(screen.getByText('編集モーダル:読み込み中')).toBeInTheDocument();
  });

  it('検索は 0 起点の page/size/search のペイロードで再取得すること', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.change(screen.getByLabelText('従業員を検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: '山田',
        storeId: undefined,
      })
    );
  });

  // 店舗の選択は検索ボタンを待たずに即時適用する（1 ページ目から取り直す）
  it('店舗を選ぶと storeId 付きで即時に取り直すこと', async () => {
    givenStores([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    await pickStore('店舗A');

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
        storeId: 9,
      })
    );
  });

  // 「すべての店舗」は絞り込み無し（番兵値であって店舗 id ではない）
  it('すべての店舗へ戻すと storeId なしで取り直すこと', async () => {
    givenStores([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await pickStore('店舗A');
    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ storeId: 9 }))
    );

    await pickStore('すべての店舗');

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
        storeId: undefined,
      })
    );
  });

  // クリアは入力を空にすると同時に取り直す。検索語 state をそのまま読むと更新前の値で
  // 取得してしまうため、適用済み検索語は ref で持っている（その回帰を固定する）。
  // 店舗の絞り込みはクリアの対象外（消したいのは検索語だけ）。
  it('クリアは検索語だけを空にし、店舗の絞り込みは保つこと', async () => {
    givenStores([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await pickStore('店舗A');

    fireEvent.change(screen.getByLabelText('従業員を検索'), { target: { value: '山田' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: '山田',
        storeId: 9,
      })
    );

    fireEvent.click(screen.getByRole('button', { name: 'クリア' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
        storeId: 9,
      })
    );
    expect(screen.getByLabelText('従業員を検索')).toHaveValue('');
  });

  // 絞り込みで 0 件のときに「登録されていません」と言うと、他店舗に居る従業員の存在を否定してしまう
  it('店舗で絞り込んで 0 件のときは「該当なし」の文言を出すこと', async () => {
    givenStores([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    mockedStaffApi.list.mockResolvedValue(paginated([]));
    await pickStore('店舗A');

    expect(await screen.findByText('該当する従業員が見つかりません')).toBeInTheDocument();
  });

  it('ページ番号のクリックで該当ページを取得すること', async () => {
    mockedStaffApi.list.mockImplementation(({ page }) =>
      Promise.resolve(
        paginated([staff({ id: 1, display_name: '山田太郎' })], { page, pageCount: 3, total: 25 })
      )
    );

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 1,
        size: 10,
        search: undefined,
        storeId: undefined,
      })
    );
  });
});

describe('従業員管理ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    givenStores([]);
    mockedReadClaims.mockReturnValue(null);
    mockedStaffApi.list.mockResolvedValue(paginated([]));
  });

  it('見出し（h1）・副題を備え、主アクションが button ロールのままであること', async () => {
    render(<StaffPage />);
    await screen.findByText('従業員が登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: '従業員管理' })).toBeInTheDocument();
    expect(
      screen.getByText('従業員のロール・担当店舗・アカウント状態を管理します。')
    ).toBeInTheDocument();
    // e2e（staff-management）は button ロールで取得するため、リンク化してはならない
    expect(screen.getByRole('button', { name: '従業員を追加' })).toBeInTheDocument();
  });
});

describe('行の操作可否', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    givenStores([]);
    mockedReadClaims.mockReturnValue(claimsWith('STAFF_MANAGE', 'STAFF_CREDENTIAL_MANAGE'));
  });

  // editable=false の行は見えるが編集・停止・再設定のいずれもできない（委譲守衛の判定）
  it('editable=false の行には操作を出さず、権限が及ばないことを名乗ること', async () => {
    mockedStaffApi.list.mockResolvedValue(
      paginated([staff({ id: 1, display_name: '本部の人', editable: false })])
    );

    render(<StaffPage />);
    await screen.findByText('本部の人');

    expect(screen.getByText('操作権限なし')).toBeInTheDocument();
    for (const name of ['編集', '停止', '再開', 'パスワード再設定'])
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
  });

  // アカウント操作は一覧とは別の権限。持たない行使者には導線ごと出さない（fail-closed）
  it('STAFF_CREDENTIAL_MANAGE を持たなければ編集だけを出すこと', async () => {
    mockedReadClaims.mockReturnValue(claimsWith('STAFF_MANAGE'));
    mockedStaffApi.list.mockResolvedValue(paginated([staff({ id: 1, display_name: '山田太郎' })]));

    render(<StaffPage />);
    await screen.findByText('山田太郎');

    expect(screen.getByRole('button', { name: '編集' })).toBeInTheDocument();
    for (const name of ['停止', '再開', 'パスワード再設定'])
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
  });
});

describe('停止・再開', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    givenStores([]);
    mockedReadClaims.mockReturnValue(claimsWith('STAFF_MANAGE', 'STAFF_CREDENTIAL_MANAGE'));
    mockedStaffApi.suspend.mockResolvedValue(undefined);
    mockedStaffApi.resume.mockResolvedValue(undefined);
    mockedStaffApi.list.mockResolvedValue(
      paginated([
        staff({ id: 1, display_name: '山田太郎', enabled: true }),
        staff({ id: 2, display_name: '鈴木花子', enabled: false }),
      ])
    );
  });

  it('有効な行には停止だけ、停止中の行には再開だけを置くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    expect(screen.getAllByRole('button', { name: '停止' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: '再開' })).toHaveLength(1);
  });

  // 停止は対象のセッションを即時に失効させるため、確認を経てからでないと実行しない
  it('停止は確認してから実行し、成功後に一覧を取り直すこと', async () => {
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));

    expect(await screen.findByText('アカウントを停止しますか？')).toBeInTheDocument();
    expect(mockedStaffApi.suspend).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '停止する' }));

    await waitFor(() => expect(mockedStaffApi.suspend).toHaveBeenCalledWith(1));
    expect(notify.success).toHaveBeenCalledWith('アカウントを停止しました');
    await waitFor(() => expect(mockedStaffApi.list).toHaveBeenCalledTimes(2));
  });

  it('確認をキャンセルすると停止しないこと', async () => {
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: '停止' }));
    await screen.findByText('アカウントを停止しますか？');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    await waitFor(() =>
      expect(screen.queryByText('アカウントを停止しますか？')).not.toBeInTheDocument()
    );
    expect(mockedStaffApi.suspend).not.toHaveBeenCalled();
  });

  // 拒否の理由（自分自身・最後の管理権限保持者・委譲外）はサーバだけが持つので、そのまま出す
  it('停止の拒否はサーバの文言をそのまま通知すること', async () => {
    mockedStaffApi.suspend.mockRejectedValue({
      response: { data: { error: '最後の管理権限保持者を停止・降格することはできません' } },
    });

    render(<StaffPage />);
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
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再開' }));

    await waitFor(() => expect(mockedStaffApi.resume).toHaveBeenCalledWith(2));
    expect(notify.success).toHaveBeenCalledWith('アカウントを再開しました');
    await waitFor(() => expect(mockedStaffApi.list).toHaveBeenCalledTimes(2));
  });

  it('再開の失敗はサーバの文言をそのまま通知すること', async () => {
    mockedStaffApi.resume.mockRejectedValue({
      response: { data: { error: 'アカウントが見つかりません: 2' } },
    });

    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: '再開' }));

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('アカウントが見つかりません: 2'));
  });
});

describe('パスワード再設定', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    givenStores([]);
    mockedReadClaims.mockReturnValue(claimsWith('STAFF_MANAGE', 'STAFF_CREDENTIAL_MANAGE'));
    mockedStaffApi.resetPassword.mockResolvedValue({
      temporary_password: 'test-temporary-password',
    });
    mockedStaffApi.list.mockResolvedValue(
      paginated([staff({ id: 1, display_name: '山田太郎', enabled: true })])
    );
  });

  const openIssuedModal = async () => {
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'パスワード再設定' }));
    fireEvent.click(await screen.findByRole('button', { name: '再設定する' }));
    return screen.findByDisplayValue('test-temporary-password');
  };

  // 対象のセッションを即時に失効させるうえ、生値は一度しか出ないので確認を挟む
  it('確認してから再設定し、発行された仮パスワードを表示すること', async () => {
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'パスワード再設定' }));

    expect(await screen.findByText('パスワードを再設定しますか？')).toBeInTheDocument();
    expect(mockedStaffApi.resetPassword).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '再設定する' }));

    await waitFor(() => expect(mockedStaffApi.resetPassword).toHaveBeenCalledWith(1));
    expect(await screen.findByDisplayValue('test-temporary-password')).toBeInTheDocument();
    // 表示名は一意でないため、取り違え防止の同定は一意な email が担う。
    expect(screen.getByText('山田太郎（staff@example.com）の仮パスワード')).toBeInTheDocument();
  });

  it('確認をキャンセルすると再設定しないこと', async () => {
    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'パスワード再設定' }));
    await screen.findByText('パスワードを再設定しますか？');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));

    await waitFor(() =>
      expect(screen.queryByText('パスワードを再設定しますか？')).not.toBeInTheDocument()
    );
    expect(mockedStaffApi.resetPassword).not.toHaveBeenCalled();
  });

  // 応答は一度きり表示の値を運び、表示枠は 1 つしかない。重ねて実行できると
  // どちらか一方の有効な仮パスワードが誰にも見えないまま失われるため、進行中は入口を塞ぐ。
  it('再設定の進行中は全行の再設定ボタンが無効になり、応答後に戻ること', async () => {
    mockedStaffApi.list.mockResolvedValue(
      paginated([
        staff({ id: 1, display_name: '山田太郎', enabled: true }),
        staff({ id: 2, display_name: '佐藤次郎', enabled: true }),
      ])
    );
    let resolveFirst: (value: { temporary_password: string }) => void = () => {};
    mockedStaffApi.resetPassword.mockImplementationOnce(
      () => new Promise(resolve => (resolveFirst = resolve))
    );

    render(<StaffPage />);
    const buttons = await screen.findAllByRole('button', { name: 'パスワード再設定' });
    fireEvent.click(buttons[0]);
    fireEvent.click(await screen.findByRole('button', { name: '再設定する' }));

    await waitFor(() => expect(mockedStaffApi.resetPassword).toHaveBeenCalledTimes(1));
    buttons.forEach(button => expect(button).toBeDisabled());

    await act(async () => {
      resolveFirst({ temporary_password: 'test-temporary-password' });
    });

    expect(screen.getByDisplayValue('test-temporary-password')).toBeInTheDocument();
    buttons.forEach(button => expect(button).toBeEnabled());
  });

  // 対象外（管理者・自分自身・委譲外）の判定はサーバだけが持つので、そのまま出す
  it('再設定の拒否はサーバの文言をそのまま通知すること', async () => {
    mockedStaffApi.resetPassword.mockRejectedValue({
      response: { data: { error: 'このアカウントのパスワードは再設定できません' } },
    });

    render(<StaffPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'パスワード再設定' }));
    fireEvent.click(await screen.findByRole('button', { name: '再設定する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('このアカウントのパスワードは再設定できません')
    );
    expect(screen.queryByDisplayValue('test-temporary-password')).not.toBeInTheDocument();
  });

  // 生値はこの表示にしか無く、誤って閉じると再発行しかやり直す手段が無い
  it('ESC では仮パスワードの表示を閉じないこと', async () => {
    await openIssuedModal();

    fireEvent.keyDown(document, { key: 'Escape', code: 'Escape' });

    await waitFor(() =>
      expect(screen.getByDisplayValue('test-temporary-password')).toBeInTheDocument()
    );
  });

  it('背景クリックでは仮パスワードの表示を閉じないこと', async () => {
    await openIssuedModal();

    const backdrop = document.querySelector('[data-slot="dialog-overlay"]');
    expect(backdrop).not.toBeNull();
    fireEvent.click(backdrop as Element);

    await waitFor(() =>
      expect(screen.getByDisplayValue('test-temporary-password')).toBeInTheDocument()
    );
  });

  it('「閉じる」ボタンでだけ仮パスワードの表示を閉じること', async () => {
    await openIssuedModal();

    fireEvent.click(screen.getByRole('button', { name: '閉じる' }));

    await waitFor(() =>
      expect(screen.queryByDisplayValue('test-temporary-password')).not.toBeInTheDocument()
    );
  });
});

test('同じ ID の連続競合再取得では最後に開始した応答だけを採用する', async () => {
  let resolveOld!: (value: StaffResponse) => void;
  mockedStaffApi.list.mockResolvedValue(paginated([staff({ id: 1, display_name: '一覧' })]));
  mockedStaffApi.get
    .mockResolvedValueOnce(staff({ id: 1, display_name: '初期' }))
    .mockReturnValueOnce(
      new Promise(done => {
        resolveOld = done;
      })
    )
    .mockResolvedValueOnce(staff({ id: 1, display_name: '最新', version: 3 }));
  render(<StaffPage />);
  fireEvent.click(await screen.findByRole('button', { name: '編集' }));
  await screen.findByText('編集モーダル:初期');
  fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));
  fireEvent.click(screen.getByRole('button', { name: '競合再取得' }));
  await screen.findByText('編集モーダル:最新');
  await act(async () => resolveOld(staff({ id: 1, display_name: '古い応答', version: 2 })));
  expect(screen.getByText('編集モーダル:最新')).toBeInTheDocument();
  expect(screen.queryByText('編集モーダル:古い応答')).not.toBeInTheDocument();
});
