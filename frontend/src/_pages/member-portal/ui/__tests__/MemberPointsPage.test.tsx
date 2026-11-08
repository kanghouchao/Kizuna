import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberPointsPage } from '../MemberPointsPage';
import { memberPointApi } from '@/entities/point';
import { memberVisitApi } from '@/entities/order';

jest.mock('@/entities/point', () => ({
  ...jest.requireActual('@/entities/point/model/types'),
  memberPointApi: { balance: jest.fn(), entries: jest.fn() },
}));

jest.mock('@/entities/order', () => ({
  memberVisitApi: { list: jest.fn() },
}));

const mockedVisits = memberVisitApi.list as jest.Mock;
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

const visits = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    visited_on: '2026-08-10',
    store_name: `来店店舗${i}`,
    granted_points: 100,
  }));

describe('MemberPointsPage', () => {
  afterEach(() => jest.useRealTimers());

  beforeEach(() => {
    jest.useFakeTimers({
      now: new Date('2026-11-08T12:00:00+09:00'),
      doNotFake: ['setTimeout', 'setInterval', 'queueMicrotask'],
    });
    jest.clearAllMocks();
    mockedVisits.mockResolvedValue(page([]));
    mockedBalance.mockResolvedValue({ balance: 1200 });
    mockedEntries.mockResolvedValue(page([]));
  });

  it('残高・明細・来店履歴をこの順に縦に並べる', async () => {
    render(<MemberPointsPage />);

    await screen.findByText('1,200 ポイント');
    expect(screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent)).toEqual([
      '現在の残高',
      '明細',
      '来店履歴',
    ]);
  });

  it('明細は種別・日付・店舗・有効期限と符号付きの増減で出す', async () => {
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

    const entries = within(await screen.findByRole('list', { name: 'ポイントの明細' }));
    const [use, grant] = entries.getAllByRole('listitem');
    expect(within(use).getByText('利用')).toBeInTheDocument();
    expect(within(use).getByText('8/11（火）・店舗B')).toBeInTheDocument();
    // 加算と減算が同じ一本に並ぶため、符号が無いとどちらか読めない
    expect(within(use).getByText('−300 ポイント')).toBeInTheDocument();
    expect(within(grant).getByText('獲得')).toBeInTheDocument();
    expect(within(grant).getByText('+500 ポイント')).toBeInTheDocument();
    expect(within(grant).getByText('有効期限 2027/8/10（火）')).toBeInTheDocument();
  });

  it('発生店舗を持たない仕訳も行として出す', async () => {
    // 失効は複数ロットに跨る系統イベントなので発生店舗を持たない。店舗名が無いだけで行は残る。
    mockedEntries.mockResolvedValue(
      page([{ occurred_on: '2026-08-11', entry_type: 'EXPIRE', amount: -100 }])
    );

    render(<MemberPointsPage />);

    const entries = within(await screen.findByRole('list', { name: 'ポイントの明細' }));
    expect(entries.getByText('失効')).toBeInTheDocument();
    expect(entries.getByText('8/11（火）')).toBeInTheDocument();
  });

  it('来店履歴は最近の 5 件と、すべてへの導線を出す', async () => {
    mockedVisits.mockResolvedValue(page(visits(7)));

    render(<MemberPointsPage />);

    const recent = within(await screen.findByRole('list', { name: '来店履歴' }));
    expect(recent.getAllByRole('listitem')).toHaveLength(5);
    expect(recent.getByText('来店店舗0')).toBeInTheDocument();
    expect(recent.queryByText('来店店舗5')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'すべて見る' })).toHaveAttribute(
      'href',
      '/member/visits'
    );
    expect(mockedVisits).toHaveBeenCalledWith(0);
  });

  it('来店が無ければ来店履歴の空を名乗り、すべてへの導線は出さない', async () => {
    render(<MemberPointsPage />);

    expect(await screen.findByText('まだ来店の記録はありません')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'すべて見る' })).not.toBeInTheDocument();
  });

  it('来店履歴だけが読めなくても、残高と明細は読めたまま来店履歴の区画が失敗を名乗る', async () => {
    mockedVisits.mockRejectedValueOnce(new Error('failed'));
    mockedEntries.mockImplementation(pagedServer(1));

    render(<MemberPointsPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('来店履歴を取得できませんでした');
    expect(screen.getByText('1,200 ポイント')).toBeInTheDocument();
    expect(await screen.findByText(/・店舗0$/)).toBeInTheDocument();

    mockedVisits.mockResolvedValue(page(visits(1)));
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('来店店舗0')).toBeInTheDocument();
  });

  it('取得完了までは区画ごとに読み込み中を表示する', () => {
    mockedBalance.mockReturnValue(new Promise(() => {}));
    mockedEntries.mockReturnValue(new Promise(() => {}));
    mockedVisits.mockReturnValue(new Promise(() => {}));

    render(<MemberPointsPage />);

    expect(screen.getAllByRole('status', { name: '読み込み中' })).toHaveLength(3);
  });

  it('残高だけが読めなくても、明細は読めたまま残高の領域が失敗を名乗る', async () => {
    mockedBalance.mockRejectedValueOnce(new Error('failed'));
    mockedEntries.mockResolvedValue(
      page([{ occurred_on: '2026-08-11', store_name: '店舗A', entry_type: 'USE', amount: -300 }])
    );

    render(<MemberPointsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('残高を取得できませんでした');
    expect(screen.getByText('−300 ポイント')).toBeInTheDocument();

    mockedBalance.mockResolvedValue({ balance: 1200 });
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('1,200 ポイント')).toBeInTheDocument();
  });

  it('明細だけが読めなくても、残高は読めたまま明細の領域が失敗を名乗る', async () => {
    mockedEntries.mockRejectedValueOnce(new Error('failed'));
    mockedEntries.mockImplementation(pagedServer(1));

    render(<MemberPointsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('明細を取得できませんでした');
    expect(screen.getByText('1,200 ポイント')).toBeInTheDocument();
    expect(screen.queryByText('まだポイントの増減はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText(/・店舗0$/)).toBeInTheDocument();
  });

  it('明細はページ番号で送り、次のページだけを表示する', async () => {
    mockedEntries.mockImplementation(pagedServer(45));

    render(<MemberPointsPage />);

    expect(await screen.findByText(/・店舗0$/)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    expect(await screen.findByText(/・店舗20$/)).toBeInTheDocument();
    expect(screen.queryByText(/・店舗0$/)).not.toBeInTheDocument();
    expect(
      within(screen.getByRole('list', { name: 'ポイントの明細' })).getAllByRole('listitem')
    ).toHaveLength(20);
    await waitFor(() => expect(mockedEntries).toHaveBeenLastCalledWith(1));
  });

  it('増減が無ければ空を名乗る', async () => {
    render(<MemberPointsPage />);

    expect(await screen.findByText('まだポイントの増減はありません')).toBeInTheDocument();
  });
});
