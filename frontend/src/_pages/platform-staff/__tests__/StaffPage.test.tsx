import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PageResult } from '@/shared/api';
import { PlatformStaffResponse, platformAuthApi, platformStaffApi } from '@/entities/user';
import StaffPage from '../ui/StaffPage';

jest.mock('@/entities/user', () => ({
  platformStaffApi: { list: jest.fn(), get: jest.fn() },
  platformAuthApi: { stores: jest.fn() },
}));

// モーダルは開くまで mount されないため、mock は mount = 表示として描画する
jest.mock('@/features/staff-management', () => {
  const React = require('react');
  return {
    StaffCreateModal: () => React.createElement('div', null, '作成モーダル表示中'),
    StaffEditModal: ({
      staff,
      onUpdated,
    }: {
      staff: { display_name: string };
      onUpdated: () => void;
    }) =>
      React.createElement(
        'div',
        null,
        `編集モーダル:${staff.display_name}`,
        // 409 で本体が呼ぶ一覧再取得を、テストから起こせるようにする
        React.createElement('button', { onClick: onUpdated }, '競合再取得')
      ),
    roleSetLabel: () => 'ロールラベル',
  };
});

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedStaffApi = platformStaffApi as jest.Mocked<typeof platformStaffApi>;
const mockedAuthApi = platformAuthApi as jest.Mocked<typeof platformAuthApi>;

const staff = (override: Partial<PlatformStaffResponse>): PlatformStaffResponse => ({
  id: 1,
  email: 'staff@example.com',
  display_name: '山田太郎',
  enabled: true,
  roles: [],
  store_scope_type: 'ALL_STORES',
  store_ids: [],
  version: 0,
  ...override,
});

const paginated = (
  rows: PlatformStaffResponse[],
  override: Partial<PageResult<PlatformStaffResponse>> = {}
): PageResult<PlatformStaffResponse> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

/**
 * 店舗の絞り込みを選ぶ。Radix Select はポインタ系 API が jsdom に無いため、
 * キーボードで開いて項目をクリックする（store-shifts のセレクト操作に倣う）。
 */
async function pickStore(optionName: string) {
  fireEvent.keyDown(screen.getByRole('combobox', { name: '店舗で絞り込む' }), { key: 'ArrowDown' });
  fireEvent.click(await screen.findByRole('option', { name: optionName }));
}

describe('スタッフ一覧ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
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
  it('行内の編集ボタンで対象スタッフの編集モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[0]);

    expect(screen.getByText('編集モーダル:山田太郎')).toBeInTheDocument();
  });

  it('行そのものをクリックしても編集モーダルは開かないこと', async () => {
    render(<StaffPage />);

    fireEvent.click(await screen.findByText('鈴木花子'));

    expect(screen.queryByText('編集モーダル:鈴木花子')).not.toBeInTheDocument();
  });

  it('スタッフを追加ボタンで作成モーダルが開くこと', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));

    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });

  // 他管理者の店舗追加・削除に追随するため、目録が取得済みでも開くたびに取り直す
  it('目録が取得済みでも、モーダルを開くたびに取り直すこと', async () => {
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));

    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(2));
  });

  // 店舗目録の取得をページ 1 回に束ねたため、初回取得の失敗はモーダルを開く時点で取り直す
  // （回復経路が無いと個別店舗の選択肢が空のまま提出できてしまう）
  it('店舗目録の取得に失敗していても、モーダルを開く時点で取り直すこと', async () => {
    mockedAuthApi.stores.mockRejectedValueOnce(new Error('network'));

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));

    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(2));
    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });

  // 開いた瞬間はまだ読み込み中で、その後に失敗が確定する時序でも取り直す
  it('モーダルを開いた後に店舗目録の取得失敗が確定しても、取り直すこと', async () => {
    let rejectFirst!: (reason: Error) => void;
    mockedAuthApi.stores.mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectFirst = reject;
        })
    );

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));
    expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1);

    rejectFirst(new Error('network'));

    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(2));
    expect(screen.getByText('作成モーダル表示中')).toBeInTheDocument();
  });

  // 409 の再取得は「最新の内容を確認してください」と言うための導線。対象が現在ページから
  // 外れても（本人の改名で検索から外れる・他の追加で次ページへずれる）モーダルは閉じず、
  // 版が古いまま再試行が 409 を繰り返さないよう id で最新値を取り直す。
  it('再取得で対象が現在ページから外れても、id で最新値を取り直してモーダルを開いたままにすること', async () => {
    mockedStaffApi.get.mockResolvedValue(
      staff({ id: 2, display_name: '鈴木花子（改名後）', version: 3 })
    );

    render(<StaffPage />);
    await screen.findByText('鈴木花子');
    fireEvent.click(screen.getAllByRole('button', { name: '編集' })[1]);
    expect(screen.getByText('編集モーダル:鈴木花子')).toBeInTheDocument();

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
    expect(screen.getByText('編集モーダル:鈴木花子')).toBeInTheDocument();
  });

  it('検索は 0 起点の page/size/search のペイロードで再取得すること', async () => {
    render(<StaffPage />);
    await screen.findByText('山田太郎');

    fireEvent.change(screen.getByLabelText('スタッフを検索'), { target: { value: '山田' } });
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
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));

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
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));
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
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));
    await pickStore('店舗A');

    fireEvent.change(screen.getByLabelText('スタッフを検索'), { target: { value: '山田' } });
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
    expect(screen.getByLabelText('スタッフを検索')).toHaveValue('');
  });

  // 絞り込みで 0 件のときに「登録されていません」と言うと、他店舗に居るスタッフの存在を否定してしまう
  it('店舗で絞り込んで 0 件のときは「該当なし」の文言を出すこと', async () => {
    mockedAuthApi.stores.mockResolvedValue([{ id: 9, name: '店舗A' }]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));

    mockedStaffApi.list.mockResolvedValue(paginated([]));
    await pickStore('店舗A');

    expect(await screen.findByText('該当するスタッフが見つかりません')).toBeInTheDocument();
  });

  // 選択中の店舗が取り直した目録から消える（他管理者の削除）と、トリガー表示が空白のまま
  // 絞り込みだけが効き続ける見えない状態になるため、「すべての店舗」へ戻して取り直す
  it('取り直した目録から選択中の店舗が消えたら、すべての店舗へ戻して取り直すこと', async () => {
    mockedAuthApi.stores.mockResolvedValue([
      { id: 9, name: '店舗A' },
      { id: 10, name: '店舗B' },
    ]);

    render(<StaffPage />);
    await screen.findByText('山田太郎');
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(1));
    await pickStore('店舗A');
    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ storeId: 9 }))
    );

    // モーダルを開くと目録を取り直す既存挙動を使い、店舗A が消えた目録を届ける
    mockedAuthApi.stores.mockResolvedValue([{ id: 10, name: '店舗B' }]);
    fireEvent.click(screen.getByRole('button', { name: 'スタッフを追加' }));
    await waitFor(() => expect(mockedAuthApi.stores).toHaveBeenCalledTimes(2));

    await waitFor(() =>
      expect(mockedStaffApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
        storeId: undefined,
      })
    );
    expect(screen.getByRole('combobox', { name: '店舗で絞り込む' })).toHaveTextContent(
      'すべての店舗'
    );
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

describe('スタッフ一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedAuthApi.stores.mockResolvedValue([]);
    mockedStaffApi.list.mockResolvedValue(paginated([]));
  });

  it('見出し（h1）・副題を備え、主アクションが button ロールのままであること', async () => {
    render(<StaffPage />);
    await screen.findByText('スタッフが登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: 'スタッフ管理' })).toBeInTheDocument();
    expect(screen.getByText('ロール・担当店舗の付与と編集ができます。')).toBeInTheDocument();
    // e2e（staff-management）は button ロールで取得するため、リンク化してはならない
    expect(screen.getByRole('button', { name: 'スタッフを追加' })).toBeInTheDocument();
  });
});
