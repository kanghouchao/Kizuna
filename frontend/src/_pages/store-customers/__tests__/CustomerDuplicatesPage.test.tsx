import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import CustomerDuplicatesPage from '../ui/CustomerDuplicatesPage';
import {
  CustomerDuplicateGroupResponse,
  CustomerMergeComparisonResponse,
  customerApi,
} from '@/entities/customer';
import { CursorPageResult } from '@/shared/api';

jest.mock('@/entities/customer', () => ({
  customerApi: { duplicates: jest.fn(), merge: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedDuplicates = customerApi.duplicates as jest.Mock;
const mockedMerge = customerApi.merge as jest.Mock;

function candidate(
  overrides: Partial<CustomerMergeComparisonResponse>
): CustomerMergeComparisonResponse {
  return { member_linked: false, order_count: 0, ...overrides };
}

/** 同じ番号の 2 行。氏名以外にも食い違う項目を持たせ、見比べる材料が出ることを確かめられるようにする。 */
const twoRowGroup: CursorPageResult<CustomerDuplicateGroupResponse> = {
  rows: [
    {
      phone_number: '090-1111-2222',
      total: 2,
      customers: [
        candidate({
          id: 'c1',
          name: '山田太郎',
          phone_number: '090-1111-2222',
          address: '東京都渋谷区1-1',
          classification: '常連',
          ng_type: '注意',
          order_count: 3,
        }),
        candidate({
          id: 'c2',
          name: 'ヤマダタロウ',
          phone_number: '090-1111-2222',
          address: '東京都新宿区2-2',
          classification: '新規',
          member_linked: true,
        }),
      ],
    },
  ],
  nextCursor: null,
};

/** 2 行を見比べる状態まで進める（どのテストも本題はその先なので、ここまでを 1 つにまとめる）。 */
async function selectBothRows() {
  fireEvent.click(await screen.findByLabelText('山田太郎 を見比べる'));
  fireEvent.click(screen.getByLabelText('ヤマダタロウ を見比べる'));
}

/** 存続行を選んで確認まで開く。 */
async function openConfirmation(survivingName = '山田太郎') {
  await selectBothRows();
  await screen.findByRole('table', { name: '2 行の比較' });
  fireEvent.click(screen.getByLabelText(`${survivingName} を残す`));
  fireEvent.click(screen.getByRole('button', { name: '統合する' }));
}

/**
 * 確認ダイアログの実行ボタン。比較区画にも同じ文言のボタンがあるので、必ずダイアログの中から取る
 * （画面の外から名前だけで取ると、確認を開くだけのボタンを押して緑になる）。
 */
async function confirmMerge() {
  const dialog = await screen.findByRole('dialog');
  fireEvent.click(within(dialog).getByRole('button', { name: '統合する' }));
}

describe('CustomerDuplicatesPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedDuplicates.mockResolvedValue(twoRowGroup);
    mockedMerge.mockResolvedValue({
      surviving_customer_id: 'c1',
      moved_order_count: 0,
      moved_link_count: 1,
    });
  });

  it('候補をバックエンドが返す snake_case のまま並べ、受注件数と紐づけの有無を出すこと', async () => {
    render(<CustomerDuplicatesPage />);

    expect(await screen.findByText('090-1111-2222')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('ヤマダタロウ')).toBeInTheDocument();
    // 見比べる材料が無いと、人手の確認が形だけになる
    expect(screen.getByText('3 件')).toBeInTheDocument();
    expect(screen.getByText('紐づけ済み')).toBeInTheDocument();
  });

  it('取得が終わるまでは読み込み中を出すこと', async () => {
    // 解決しない取得で読み込み枝に留める。空表示に落ちると「重複は無い」と嘘をつく
    mockedDuplicates.mockReturnValue(new Promise(() => {}));

    render(<CustomerDuplicatesPage />);

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('電話番号が重複している顧客はいません')).not.toBeInTheDocument();
  });

  it('取得に失敗した領域が自分で名乗り、再試行を出すこと', async () => {
    mockedDuplicates.mockRejectedValueOnce(new Error('boom'));

    render(<CustomerDuplicatesPage />);

    // 空表示に落とすと「重複は無い」と嘘をつくことになる
    expect(await screen.findByText('重複候補の取得に失敗しました')).toBeInTheDocument();
    mockedDuplicates.mockResolvedValue(twoRowGroup);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
  });

  it('候補が無ければ、取得の失敗と区別のつく空表示になること', async () => {
    mockedDuplicates.mockResolvedValue({ rows: [], nextCursor: null });

    render(<CustomerDuplicatesPage />);

    expect(await screen.findByText('電話番号が重複している顧客はいません')).toBeInTheDocument();
    expect(screen.queryByText('重複候補の取得に失敗しました')).not.toBeInTheDocument();
  });

  it('続きがあるときは、続きを辿る導線を出すこと', async () => {
    // 上限で黙って切ると、番号を共有する同伴者のような正当な偽陽性が先頭を占めたとき
    // 以降の真の重複が一生画面に出ない
    mockedDuplicates.mockResolvedValueOnce({ ...twoRowGroup, nextCursor: 'MDkw' });

    render(<CustomerDuplicatesPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));

    await waitFor(() => expect(mockedDuplicates).toHaveBeenLastCalledWith({ cursor: 'MDkw' }));
  });

  it('続きが無ければ、続きを辿る導線を出さないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await screen.findByText('山田太郎');

    expect(screen.queryByRole('button', { name: 'さらに読み込む' })).not.toBeInTheDocument();
  });

  it('行を並べない桁外れのグループは、総数と統合できる画面を案内すること', async () => {
    // 桁外れのグループは行が返らない（標本は本人を見分ける材料にならない）。総数だけは偽らない
    mockedDuplicates.mockResolvedValue({
      rows: [{ phone_number: '0000000000', total: 200, customers: [] }],
      nextCursor: null,
    });

    render(<CustomerDuplicatesPage />);

    expect(await screen.findByText('200 件')).toBeInTheDocument();
    // 案内は実際に統合できる画面を指すこと（顧客一覧で 2 行を選ぶ経路）
    expect(screen.getByText(/顧客一覧で選んでください/)).toBeInTheDocument();
    // 選べない行を並べない
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('2 行を選ぶと、両行の内容が並べて表示されること', async () => {
    render(<CustomerDuplicatesPage />);
    await selectBothRows();

    const comparison = await screen.findByRole('table', { name: '2 行の比較' });
    // 住所は一覧の型には無く、別人かどうかの判断はここで分かれる
    expect(within(comparison).getByText('東京都渋谷区1-1')).toBeInTheDocument();
    expect(within(comparison).getByText('東京都新宿区2-2')).toBeInTheDocument();
    expect(within(comparison).getByText('常連')).toBeInTheDocument();
    expect(within(comparison).getByText('新規')).toBeInTheDocument();
    expect(within(comparison).getByText('3 件')).toBeInTheDocument();
  });

  it('未設定のペット有無を「なし」と断定しないこと', async () => {
    // 応答は non_null 直列化なので未設定は欄ごと欠けて届く。真偽値へ潰すと、別人を見分ける
    // ための画面が持っていない事実を断言する
    render(<CustomerDuplicatesPage />);
    await selectBothRows();

    const comparison = await screen.findByRole('table', { name: '2 行の比較' });
    const petRow = within(comparison).getByText('ペット').closest('tr');
    expect(within(petRow!).queryByText('なし')).not.toBeInTheDocument();
    expect(within(petRow!).getAllByText('-')).toHaveLength(2);
  });

  it('存続行を選ぶまでは統合できないこと（機械が残す行を決めない）', async () => {
    render(<CustomerDuplicatesPage />);
    await selectBothRows();

    expect(await screen.findByText('台帳に残す行を選んでください。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '統合する' })).toBeDisabled();
  });

  it('確認を経ずに統合が実行されないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();

    // 「統合する」は確認を開くだけ。ここで走ってしまうと取り返しがつかない
    expect(await screen.findByText('顧客を統合しますか？')).toBeInTheDocument();
    expect(mockedMerge).not.toHaveBeenCalled();
  });

  it('確認で統合が取り消せないことを明示すること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();

    expect(await screen.findByText(/統合は取り消せません/)).toBeInTheDocument();
    // 転記の期限は「今」。統合後は被統合行にしかない値を読む経路が無い（一覧からも候補からも
    // 外れ、旧 ID の詳細は統合先の行を返す）ので、「後で転記できる」と読ませてはならない
    expect(screen.getByText(/統合後どこからも読めなくなります/)).toBeInTheDocument();
    expect(screen.getByText(/キャンセルして先に転記/)).toBeInTheDocument();
  });

  it('確認は ESC でも背景押下でも閉じないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    const title = await screen.findByText('顧客を統合しますか？');

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape', code: 'Escape' });
    await waitFor(() => expect(title).toBeInTheDocument());
    const backdrop = document.querySelector('[data-slot="dialog-overlay"]');
    fireEvent.pointerDown(backdrop!);
    fireEvent.click(backdrop!);

    // 取り返しのつかない確認が「うっかり触れた」で消えない
    await waitFor(() => expect(screen.getByText('顧客を統合しますか？')).toBeInTheDocument());
  });

  it('確認を承けて、存続行と被統合行を指して統合すること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    await confirmMerge();

    await waitFor(() => expect(mockedMerge).toHaveBeenCalledWith('c1', 'c2'));
    expect(notify.success).toHaveBeenCalledWith('顧客を統合しました');
  });

  it('統合の成功後、被統合行が一覧から消えていること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    // 畳んだ番号はグループを成さなくなるので、取り直した候補から丸ごと落ちる
    mockedDuplicates.mockResolvedValue({ rows: [], nextCursor: null });
    await confirmMerge();

    expect(await screen.findByText('電話番号が重複している顧客はいません')).toBeInTheDocument();
    expect(screen.queryByText('ヤマダタロウ')).not.toBeInTheDocument();
  });

  it('両行が会員に紐づいている拒否は、先に関連を解除すると読める形で画面に出ること', async () => {
    const guidance = '両方の顧客に会員が紐づいています。先に関連を解除してから統合してください';
    mockedMerge.mockRejectedValue({ response: { status: 409, data: { error: guidance } } });

    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    await confirmMerge();

    // サーバの案内を汎用文言へ潰すと、次の一手が画面から判らなくなる
    await waitFor(() => expect(notify.error).toHaveBeenCalledWith(guidance));
  });

  it('フィールド値を合併する UI・一括統合の導線を持たないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await selectBothRows();
    await screen.findByRole('table', { name: '2 行の比較' });

    // 値を選んで混ぜる UI も、まとめて畳む導線も持たない（ADR 0010）
    expect(screen.queryByRole('button', { name: /まとめて統合|一括/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /この値を使う|値を移す/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /自動統合/ })).not.toBeInTheDocument();
  });
});
