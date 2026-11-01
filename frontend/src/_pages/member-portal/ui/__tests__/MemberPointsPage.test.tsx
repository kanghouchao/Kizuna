import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberPointsPage } from '../MemberPointsPage';
import { memberPointApi } from '@/entities/point';

jest.mock('@/entities/point', () => ({
  ...jest.requireActual('@/entities/point/model/types'),
  memberPointApi: { balance: jest.fn(), entries: jest.fn() },
}));

const mockedBalance = memberPointApi.balance as jest.Mock;
const mockedEntries = memberPointApi.entries as jest.Mock;

const page = (rows: unknown[]) => ({
  rows,
  page: 0,
  pageCount: rows.length ? 1 : 0,
  total: rows.length,
});

/** ページ番号で明細を返すサーバの代役（1 ページ 20 件）。 */
const pagedServer = (total: number) => (requested: number) => {
  const start = requested * 20;
  const end = Math.min(start + 20, total);
  const rows = Array.from({ length: end - start }, (_, i) => ({
    occurred_on: '2026-08-10',
    store_name: `店舗${start + i}`,
    entry_type: 'ORDER_GRANT',
    amount: 100 + start + i,
  }));
  return Promise.resolve({ rows, page: requested, pageCount: Math.ceil(total / 20), total });
};

describe('MemberPointsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedBalance.mockResolvedValue({ balance: 1200 });
    mockedEntries.mockResolvedValue(page([]));
  });

  it('残高と、全店舗・全種別の明細を並べる', async () => {
    mockedEntries.mockResolvedValue(
      page([
        {
          occurred_on: '2026-08-11',
          store_name: '店舗B',
          entry_type: 'USE',
          amount: -300,
        },
        {
          occurred_on: '2026-08-10',
          store_name: '店舗A',
          entry_type: 'ORDER_GRANT',
          amount: 500,
          expires_on: '2027-08-10',
        },
      ])
    );

    render(<MemberPointsPage />);

    expect(await screen.findByText('1,200 pt')).toBeInTheDocument();
    // 加算と減算が同じ一本に並ぶため、符号が無いとどちらか読めない
    expect(screen.getByText('-300 pt')).toBeInTheDocument();
    expect(screen.getByText('+500 pt')).toBeInTheDocument();
    expect(screen.getByText('店舗A')).toBeInTheDocument();
    expect(screen.getByText('店舗B')).toBeInTheDocument();
    expect(screen.getByText('利用')).toBeInTheDocument();
    expect(screen.getByText('獲得')).toBeInTheDocument();
    expect(screen.getByText('有効期限: 2027-08-10')).toBeInTheDocument();
  });

  it('発生店舗を持たない仕訳も行として出す', async () => {
    // 失効は複数ロットに跨る系統イベントなので発生店舗を持たない。店舗名が無いだけで行は残る。
    mockedEntries.mockResolvedValue(
      page([{ occurred_on: '2026-08-11', entry_type: 'EXPIRE', amount: -100 }])
    );

    render(<MemberPointsPage />);

    const row = await screen.findByRole('listitem');
    expect(within(row).getByText('失効')).toBeInTheDocument();
    expect(within(row).getByText('店舗なし')).toBeInTheDocument();
  });

  it('取得完了までは読み込み中を表示する', () => {
    mockedBalance.mockReturnValue(new Promise(() => {}));
    mockedEntries.mockReturnValue(new Promise(() => {}));

    render(<MemberPointsPage />);

    expect(screen.getAllByText('読み込み中...')).toHaveLength(2);
  });

  it('残高だけが読めなくても、明細は読めたまま残高の領域が失敗を名乗る', async () => {
    mockedBalance.mockRejectedValueOnce(new Error('failed'));
    mockedEntries.mockResolvedValue(
      page([{ occurred_on: '2026-08-11', store_name: '店舗A', entry_type: 'USE', amount: -300 }])
    );

    render(<MemberPointsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('残高を取得できませんでした。');
    expect(screen.getByText('-300 pt')).toBeInTheDocument();

    mockedBalance.mockResolvedValue({ balance: 1200 });
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('1,200 pt')).toBeInTheDocument();
  });

  it('明細だけが読めなくても、残高は読めたまま明細の領域が失敗を名乗る', async () => {
    mockedEntries.mockRejectedValueOnce(new Error('failed'));
    mockedEntries.mockImplementation(pagedServer(1));

    render(<MemberPointsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('明細を取得できませんでした。');
    expect(screen.getByText('1,200 pt')).toBeInTheDocument();
    expect(screen.queryByText('ポイントの増減はまだありません。')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('明細はページ番号で送り、次のページだけを表示する', async () => {
    mockedEntries.mockImplementation(pagedServer(45));

    render(<MemberPointsPage />);

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    expect(await screen.findByText('店舗20')).toBeInTheDocument();
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(20);
    await waitFor(() => expect(mockedEntries).toHaveBeenLastCalledWith(1));
  });

  it('増減が無ければ空を名乗る', async () => {
    render(<MemberPointsPage />);

    expect(await screen.findByText('ポイントの増減はまだありません。')).toBeInTheDocument();
  });
});
