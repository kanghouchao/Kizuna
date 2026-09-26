import { mergePreviewFixture, confirmReviewedMerge } from '../testing/merge-test-support';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import CustomerDuplicatesPage from '../ui/CustomerDuplicatesPage';
import {
  CustomerDuplicateGroupResponse,
  CustomerMergeComparisonResponse,
  customerApi,
} from '@/entities/customer';
import { CursorPageResult } from '@/shared/api';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: () => ({ authorities: ['PERM_CUSTOMER_MANAGE', 'PERM_CUSTOMER_MERGE'] }),
}));

jest.mock('@/entities/customer', () => ({
  customerApi: {
    duplicates: jest.fn(),
    duplicateCustomers: jest.fn(),
    mergePreview: jest.fn(),
    merge: jest.fn(),
  },
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
  return { preferred_contacts: [], member_linked: false, order_count: 0, ...overrides };
}

/** 同じ番号の 2 行。氏名以外にも食い違う項目を持たせ、見比べる材料が出ることを確かめられるようにする。 */
const twoRowGroup: CursorPageResult<CustomerDuplicateGroupResponse> = {
  rows: [
    {
      matched_type: 'PHONE',
      matched_value: '090-1111-2222',
      total: 2,
      customers: [
        candidate({
          id: 'c1',
          name: '山田太郎',
          preferred_contacts: [{ id: 'contact1', type: 'PHONE', value: '090-1111-2222' }],
          address: '東京都渋谷区1-1',
          classification: '常連',
          ng_type: '注意',
          order_count: 3,
        }),
        candidate({
          id: 'c2',
          name: 'ヤマダタロウ',
          preferred_contacts: [{ id: 'contact1', type: 'PHONE', value: '090-1111-2222' }],
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
  await confirmReviewedMerge();
}

describe('CustomerDuplicatesPage', () => {
  beforeEach(() => {
    jest.resetAllMocks();
    (customerApi.mergePreview as jest.Mock).mockImplementation(mergePreviewFixture);
    mockedDuplicates.mockResolvedValue(twoRowGroup);
    mockedMerge.mockResolvedValue({
      surviving_customer_id: 'c1',
      moved_order_count: 0,
      moved_link_count: 1,
    });
  });

  it('メールと LINE の同じ値を別の一致種類として表示する', async () => {
    mockedDuplicates.mockResolvedValue({
      rows: (['EMAIL', 'LINE'] as const).map(type => ({
        ...twoRowGroup.rows[0],
        matched_type: type,
        matched_value: 'Case@example.com',
      })),
      nextCursor: null,
    });
    render(<CustomerDuplicatesPage />);
    expect(await screen.findByText('メール')).toBeInTheDocument();
    expect(screen.getByText('LINE ID')).toBeInTheDocument();
    expect(screen.getAllByText('Case@example.com')).toHaveLength(2);
    expect(mockedMerge).not.toHaveBeenCalled();
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
    expect(screen.queryByText('連絡先が重複している顧客はいません')).not.toBeInTheDocument();
  });

  it('取得に失敗した領域が自分で名乗り、再試行を出すこと', async () => {
    mockedDuplicates.mockRejectedValueOnce(new Error('boom'));

    render(<CustomerDuplicatesPage />);

    // 空表示に落とすと「重複は無い」と嘘をつくことになる
    expect(await screen.findByText('重複候補の取得に失敗しました')).toBeInTheDocument();
    (customerApi.mergePreview as jest.Mock).mockImplementation(mergePreviewFixture);
    mockedDuplicates.mockResolvedValue(twoRowGroup);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
  });

  it('候補が無ければ、取得の失敗と区別のつく空表示になること', async () => {
    mockedDuplicates.mockResolvedValue({ rows: [], nextCursor: null });

    render(<CustomerDuplicatesPage />);

    expect(await screen.findByText('連絡先が重複している顧客はいません')).toBeInTheDocument();
    expect(screen.queryByText('重複候補の取得に失敗しました')).not.toBeInTheDocument();
  });

  it('続きがあるときは、続きを辿る導線を出すこと', async () => {
    // 上限で黙って切ると、番号を共有する同伴者のような正当な偽陽性が先頭を占めたとき
    // 以降の真の重複が一生画面に出ない
    mockedDuplicates
      .mockResolvedValueOnce({ ...twoRowGroup, nextCursor: 'MDkw' })
      .mockResolvedValueOnce({ rows: [], nextCursor: null });

    render(<CustomerDuplicatesPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));

    await waitFor(() => expect(mockedDuplicates).toHaveBeenLastCalledWith({ cursor: 'MDkw' }));
  });

  it('続きが無ければ、続きを辿る導線を出さないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await screen.findByText('山田太郎');

    expect(screen.queryByRole('button', { name: 'さらに読み込む' })).not.toBeInTheDocument();
  });

  it.each(['close', 'escape'])('確認を中断しても展開済み候補と選択を保持する: %s', async action => {
    mockedDuplicates.mockResolvedValue({
      rows: [{ ...twoRowGroup.rows[0], total: 21, customers: [] }],
      nextCursor: null,
    });
    const members = customerApi.duplicateCustomers as jest.Mock;
    members.mockResolvedValue({ rows: twoRowGroup.rows[0].customers, nextCursor: null });
    render(<CustomerDuplicatesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '顧客を表示' }));
    await selectBothRows();
    fireEvent.click(screen.getByLabelText('山田太郎 を残す'));
    fireEvent.click(screen.getByRole('button', { name: '統合する' }));
    await screen.findByLabelText('統合理由');
    if (action === 'close') fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    else
      fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape', code: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByLabelText('山田太郎 を見比べる')).toBeChecked();
    expect(screen.getByLabelText('ヤマダタロウ を見比べる')).toBeChecked();
    expect(mockedDuplicates).toHaveBeenCalledTimes(1);
    expect(members).toHaveBeenCalledTimes(1);
  });

  it('候補群の追加取得中も展開済みの顧客ページと選択を保持する', async () => {
    let finish!: (page: CursorPageResult<CustomerDuplicateGroupResponse>) => void;
    mockedDuplicates
      .mockResolvedValueOnce({
        rows: [{ ...twoRowGroup.rows[0], total: 21, customers: [] }],
        nextCursor: 'groups-next',
      })
      .mockReturnValueOnce(
        new Promise(resolve => {
          finish = resolve;
        })
      );
    const members = customerApi.duplicateCustomers as jest.Mock;
    members
      .mockResolvedValueOnce({
        rows: [twoRowGroup.rows[0].customers[0]],
        nextCursor: 'members-next',
      })
      .mockResolvedValueOnce({ rows: [twoRowGroup.rows[0].customers[1]], nextCursor: null });
    render(<CustomerDuplicatesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '顧客を表示' }));
    fireEvent.click(await screen.findByRole('button', { name: '顧客をさらに読み込む' }));
    await screen.findByLabelText('ヤマダタロウ を見比べる');
    await selectBothRows();
    fireEvent.click(screen.getByRole('button', { name: 'さらに読み込む' }));
    expect(screen.getByRole('table', { name: '2 行の比較' })).toBeInTheDocument();
    await act(async () => finish({ rows: [], nextCursor: null }));
    expect(screen.getByLabelText('ヤマダタロウ を見比べる')).toBeChecked();
    expect(screen.getByRole('table', { name: '2 行の比較' })).toBeInTheDocument();
    expect(members).toHaveBeenCalledTimes(2);
  });

  it.each([false, true])('再試行後の最新行から統合確認を作る（展開群: %s）', async expanded => {
    const group = twoRowGroup.rows[0];
    const fresh = group.customers.map(row => ({ ...row, name: `${row.name}更新`, order_count: 9 }));
    const members = customerApi.duplicateCustomers as jest.Mock;
    if (expanded) {
      mockedDuplicates.mockResolvedValue({
        rows: [{ ...group, total: 21, customers: [] }],
        nextCursor: null,
      });
      members
        .mockResolvedValueOnce({ rows: group.customers, nextCursor: 'next' })
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce({ rows: fresh, nextCursor: null });
    } else {
      mockedDuplicates
        .mockResolvedValueOnce({ ...twoRowGroup, nextCursor: 'next' })
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce({ rows: [{ ...group, customers: fresh }], nextCursor: null });
    }
    render(<CustomerDuplicatesPage />);
    if (expanded) fireEvent.click(await screen.findByRole('button', { name: '顧客を表示' }));
    await selectBothRows();
    fireEvent.click(screen.getByLabelText('山田太郎 を残す'));
    fireEvent.click(
      screen.getByRole('button', { name: expanded ? '顧客をさらに読み込む' : 'さらに読み込む' })
    );
    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));
    const comparison = await screen.findByRole('table', { name: '2 行の比較' });
    expect(within(comparison).getAllByText('9 件')).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '統合する' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() =>
      expect(customerApi.mergePreview).toHaveBeenCalledWith('c1', { merged_customer_id: 'c2' })
    );
    expect(await within(dialog).findByText(/移動する受注 9 件/)).toBeInTheDocument();
    await confirmMerge();
    await waitFor(() =>
      expect(mockedMerge).toHaveBeenCalledWith(
        'c1',
        expect.objectContaining({ merged_customer_id: 'c2', preview_token: 'proof' })
      )
    );
  });

  it('大きい組も続きを取得でき、失敗したら先頭から再試行できる', async () => {
    mockedDuplicates.mockResolvedValue({
      rows: [{ ...twoRowGroup.rows[0], total: 21, customers: [] }],
      nextCursor: null,
    });
    const members = customerApi.duplicateCustomers as jest.Mock;
    members
      .mockResolvedValueOnce({ rows: [twoRowGroup.rows[0].customers[0]], nextCursor: 'next' })
      .mockRejectedValueOnce(new Error('failure'))
      .mockResolvedValueOnce({ rows: twoRowGroup.rows[0].customers, nextCursor: null });
    render(<CustomerDuplicatesPage />);
    expect(await screen.findByText('21 件')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '顧客を表示' }));
    await screen.findByText('山田太郎');
    fireEvent.click(screen.getByRole('button', { name: '顧客をさらに読み込む' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('候補顧客の取得に失敗しました');
    expect(screen.queryByText('山田太郎')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    await selectBothRows();
    expect(await screen.findByRole('table', { name: '2 行の比較' })).toBeInTheDocument();
    expect(members).toHaveBeenLastCalledWith({
      type: 'PHONE',
      value: '090-1111-2222',
      cursor: undefined,
    });
    expect(mockedMerge).not.toHaveBeenCalled();
  });

  it('条件変更で選択とカーソルを捨て、追加取得の失敗から同じ条件で再試行する', async () => {
    render(<CustomerDuplicatesPage />);
    await selectBothRows();
    mockedDuplicates.mockResolvedValueOnce({ ...twoRowGroup, nextCursor: 'next' });
    fireEvent.change(screen.getByLabelText('連絡先で検索'), { target: { value: '090' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    await waitFor(() =>
      expect(mockedDuplicates).toHaveBeenLastCalledWith({
        cursor: undefined,
        search: '090',
        type: undefined,
      })
    );
    expect(screen.queryByRole('table', { name: '2 行の比較' })).not.toBeInTheDocument();
    mockedDuplicates.mockRejectedValueOnce(new Error('failure'));
    fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));
    await screen.findByRole('alert');
    expect(screen.queryByText('山田太郎')).not.toBeInTheDocument();
    mockedDuplicates.mockResolvedValueOnce(twoRowGroup);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    await screen.findByText('山田太郎');
    expect(mockedDuplicates).toHaveBeenLastCalledWith({
      cursor: undefined,
      search: '090',
      type: undefined,
    });
  });

  it('同じ顧客が複数種類で一致しても選択中の組だけを比較する', async () => {
    mockedDuplicates.mockResolvedValue({
      rows: (['EMAIL', 'LINE'] as const).map(type => ({
        ...twoRowGroup.rows[0],
        matched_type: type,
        matched_value: 'Case@example.com',
      })),
      nextCursor: null,
    });
    render(<CustomerDuplicatesPage />);
    const first = await screen.findAllByLabelText('山田太郎 を見比べる');
    fireEvent.click(first[0]);
    fireEvent.click(screen.getAllByLabelText('ヤマダタロウ を見比べる')[0]);
    expect(screen.getAllByRole('table', { name: '2 行の比較' })).toHaveLength(1);
    fireEvent.click(first[1]);
    expect(screen.queryByRole('table', { name: '2 行の比較' })).not.toBeInTheDocument();
    expect(mockedMerge).not.toHaveBeenCalled();
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
    expect(await screen.findByText('顧客統合の資料と影響を確認')).toBeInTheDocument();
    expect(mockedMerge).not.toHaveBeenCalled();
  });

  it('確認で統合が取り消せないことを明示すること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();

    expect(await screen.findByText(/統合は取り消せません/)).toBeInTheDocument();
    // 転記の期限は「今」。統合後は被統合行にしかない値を読む経路が無い（一覧からも候補からも
    // 外れ、旧 ID の詳細は統合先の行を返す）ので、「後で転記できる」と読ませてはならない
    expect(screen.getByRole('button', { name: '被統合側の氏名を採用' })).toBeInTheDocument();
  });

  it('資料確認は ESC で中断しても実行しないこと', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    await screen.findByLabelText('統合理由');
    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape', code: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockedMerge).not.toHaveBeenCalled();
  });

  it('確認を承けて、存続行と被統合行を指して統合すること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    await confirmMerge();

    await waitFor(() =>
      expect(mockedMerge).toHaveBeenCalledWith(
        'c1',
        expect.objectContaining({ merged_customer_id: 'c2', preview_token: 'proof' })
      )
    );
    expect(notify.success).toHaveBeenCalledWith('顧客を統合しました');
  });

  it('退出を待たず候補を再取得し、確認文は退出完了まで保持すること', async () => {
    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    const dialog = screen.getByRole('dialog');
    let finish!: () => void;
    const finished = new Promise<void>(resolve => {
      finish = resolve;
    });
    Object.defineProperty(dialog, 'getAnimations', { value: () => [{ finished }] });
    mockedDuplicates.mockResolvedValue({ rows: [], nextCursor: null });
    await confirmMerge();

    expect(await screen.findByText('連絡先が重複している顧客はいません')).toBeInTheDocument();
    expect(mockedDuplicates).toHaveBeenCalledTimes(2);
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('data-closed');
    expect(dialog).toHaveTextContent('ヤマダタロウ');
    expect(dialog).toHaveTextContent('山田太郎');
    await act(async () => finish());
    await waitFor(() => expect(dialog).not.toBeInTheDocument());
    expect(screen.queryByText('ヤマダタロウ')).not.toBeInTheDocument();
  });

  it('両行が会員に紐づいている拒否は、先に関連を解除すると読める形で画面に出ること', async () => {
    const guidance = '両方の顧客に会員が紐づいています。先に関連を解除してから統合してください';
    mockedMerge.mockRejectedValue({ response: { status: 409, data: { error: guidance } } });

    render(<CustomerDuplicatesPage />);
    await openConfirmation();
    await confirmMerge();

    // サーバの案内を汎用文言へ潰すと、次の一手が画面から判らなくなる
    expect(await screen.findByText(guidance)).toBeInTheDocument();
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
