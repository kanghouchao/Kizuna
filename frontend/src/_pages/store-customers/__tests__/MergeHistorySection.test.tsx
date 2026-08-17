import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MergeHistorySection } from '../ui/MergeHistorySection';
import { customerApi } from '@/entities/customer';

jest.mock('@/entities/customer', () => ({
  customerApi: { mergeHistory: jest.fn() },
}));

const mockedApi = customerApi as jest.Mocked<typeof customerApi>;

/** 履歴 1 頁分。続きの有無は呼出側が指定する。 */
function historyPage(rows: unknown[], nextCursor: string | null = null) {
  return { rows, nextCursor } as never;
}

/** この顧客が存続行として受けた統合。 */
const receivedRow = {
  id: 'm2',
  direction: 'SURVIVING' as const,
  counterpart_customer_id: 'cus-old',
  counterpart_customer_name: '山田太郎（旧）',
  merged_by_name: '田中花子',
  merged_at: '2026-08-10T10:00:00+09:00',
  moved_order_count: 3,
  moved_link_count: 1,
};

/** この顧客が被統合となり、墓標になった統合。 */
const absorbedRow = {
  id: 'm1',
  direction: 'MERGED' as const,
  counterpart_customer_id: 'cus-new',
  counterpart_customer_name: '山田太郎',
  merged_by_name: '山田次郎',
  merged_at: '2026-07-01T10:00:00+09:00',
  moved_order_count: 0,
  moved_link_count: 0,
};

describe('MergeHistorySection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.mergeHistory.mockResolvedValue(historyPage([]));
  });

  it('読み込み中を経て、統合が無いことを空表示で示すこと', async () => {
    render(<MergeHistorySection customerId="c1" />);

    // 読み込み中を空表示と同じ姿で描くと、まだ取りに行っている間ずっと「無い」と言い続ける
    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('統合履歴がありません')).not.toBeInTheDocument();

    expect(await screen.findByText('統合履歴がありません')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('存続行として受けた統合と被統合となった統合が、向きの分かる形で並ぶこと', async () => {
    mockedApi.mergeHistory.mockResolvedValue(historyPage([receivedRow, absorbedRow]));

    render(<MergeHistorySection customerId="c1" />);

    const received = within(await screen.findByRole('row', { name: /山田太郎（旧）/ }));
    expect(received.getByText('存続行として受けた')).toBeInTheDocument();
    // 相手の行は名前と ID の両方を出す。誤統合の修復は「どの行をどの行へ」を根拠にする
    expect(received.getByText('cus-old')).toBeInTheDocument();
    expect(received.getByText(/田中花子・/)).toBeInTheDocument();
    expect(received.getByText('3 件')).toBeInTheDocument();
    expect(received.getByText('1 件')).toBeInTheDocument();

    const absorbed = within(screen.getByRole('row', { name: /cus-new/ }));
    expect(absorbed.getByText('被統合となった')).toBeInTheDocument();
    expect(absorbed.getByText(/山田次郎・/)).toBeInTheDocument();
  });

  it('実行者が削除済みでも行は残り、実行者だけが不明として出ること', async () => {
    mockedApi.mergeHistory.mockResolvedValue(
      historyPage([{ ...receivedRow, merged_by_name: undefined }])
    );

    render(<MergeHistorySection customerId="c1" />);

    const row = within(await screen.findByRole('row', { name: /山田太郎（旧）/ }));
    expect(row.getByText(/不明・/)).toBeInTheDocument();
    expect(row.getByText('cus-old')).toBeInTheDocument();
  });

  it('統合を取り消す導線が存在しないこと', async () => {
    mockedApi.mergeHistory.mockResolvedValue(historyPage([receivedRow, absorbedRow]));

    render(<MergeHistorySection customerId="c1" />);

    await screen.findByRole('row', { name: /山田太郎（旧）/ });
    // 統合に undo は無い（ADR 0010）。押せる導線が 1 つでも出れば、取り消せるという誤解を招く
    expect(screen.queryAllByRole('button').map(button => button.textContent)).not.toContainEqual(
      expect.stringMatching(/取消|取り消|戻す|復元/)
    );
  });

  it('取得に失敗したら空表示に化けず、区画自身が失敗を名乗って再試行できること', async () => {
    mockedApi.mergeHistory.mockRejectedValueOnce(new Error('boom'));

    render(<MergeHistorySection customerId="c1" />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('統合履歴の取得に失敗しました')).toBeInTheDocument();
    // 読めなかった履歴を「ありません」と言い切ると、読み取り失敗が事実に化ける
    expect(screen.queryByText('統合履歴がありません')).not.toBeInTheDocument();

    mockedApi.mergeHistory.mockResolvedValue(historyPage([receivedRow]));
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('row', { name: /山田太郎（旧）/ })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('続きがあれば追加で読み込め、返ったカーソルをそのまま次の要求に渡すこと', async () => {
    // Once の並びで 1 頁目・2 頁目を決める（クリック後に積むと、先に走る要求が既定値を掴む）
    mockedApi.mergeHistory
      .mockResolvedValueOnce(historyPage([receivedRow], 'cur-1'))
      .mockResolvedValueOnce(historyPage([absorbedRow]));

    render(<MergeHistorySection customerId="c1" />);

    fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));

    await waitFor(() =>
      expect(mockedApi.mergeHistory).toHaveBeenLastCalledWith('c1', { cursor: 'cur-1' })
    );
    expect(await screen.findByRole('row', { name: /cus-new/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /山田太郎（旧）/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'さらに読み込む' })).not.toBeInTheDocument();
  });
});
