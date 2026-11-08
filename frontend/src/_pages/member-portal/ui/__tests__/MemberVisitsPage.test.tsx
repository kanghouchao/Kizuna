import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberVisitsPage } from '../MemberVisitsPage';
import { memberVisitApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  memberVisitApi: { list: jest.fn() },
}));

const mockedList = memberVisitApi.list as jest.Mock;

const PAGE_SIZE = 20;

const page = (rows: unknown[], number = 0, total = rows.length) => ({
  rows,
  page: number,
  pageCount: Math.ceil(total / PAGE_SIZE),
  total,
});

/** ページ番号で行を返すサーバの代役。 */
const pagedServer = (total: number) => (number: number) => {
  const start = number * PAGE_SIZE;
  const end = Math.min(start + PAGE_SIZE, total);
  const rows = Array.from({ length: end - start }, (_, i) => ({
    visited_on: '2026-08-10',
    store_name: `店舗${start + i}`,
    pax: 2,
    cast_name: `担当${start + i}`,
    granted_points: 100 + start + i,
  }));
  return Promise.resolve(page(rows, number, total));
};

describe('MemberVisitsPage', () => {
  afterEach(() => jest.useRealTimers());

  beforeEach(() => {
    jest.useFakeTimers({
      now: new Date('2026-11-08T12:00:00+09:00'),
      doNotFake: ['setTimeout', 'setInterval', 'queueMicrotask'],
    });
    jest.clearAllMocks();
    mockedList.mockResolvedValue(page([]));
  });

  it('全店舗の来店を新しい順に、日付・店舗・獲得ポイントで並べる', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          visited_on: '2026-08-11',
          store_name: '店舗B',
          pax: 3,
          cast_name: 'キャスト太郎',
          granted_points: 120,
        },
        {
          visited_on: '2025-12-30',
          store_name: '店舗A',
          pax: 2,
          granted_points: 1080,
        },
      ])
    );

    render(<MemberVisitsPage />);

    const rows = await screen.findAllByRole('listitem');
    expect(within(rows[0]).getByText('8/11（火）')).toBeInTheDocument();
    expect(within(rows[0]).getByText('店舗B')).toBeInTheDocument();
    expect(within(rows[0]).getByText('+120 ポイント')).toBeInTheDocument();
    // 今年でない来店は年を添える
    expect(within(rows[1]).getByText('2025/12/30（火）')).toBeInTheDocument();
    expect(within(rows[1]).getByText('店舗A')).toBeInTheDocument();
    expect(within(rows[1]).getByText('+1,080 ポイント')).toBeInTheDocument();
  });

  it('付与の無い来店も行として出し、符号なしの 0 を出す', async () => {
    mockedList.mockResolvedValue(
      page([{ visited_on: '2026-08-11', store_name: '店舗A', pax: 1, granted_points: 0 }])
    );

    render(<MemberVisitsPage />);

    const row = await screen.findByRole('listitem');
    expect(within(row).getByText('店舗A')).toBeInTheDocument();
    expect(within(row).getByText('0 ポイント')).toBeInTheDocument();
  });

  it('取得完了までは読み込み中を表示する', () => {
    mockedList.mockReturnValue(new Promise(() => {}));

    render(<MemberVisitsPage />);

    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
  });

  it('取得に失敗したら失敗を名乗り、再試行で復帰できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberVisitsPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('来店履歴を取得できませんでした');
    expect(screen.queryByText('まだ来店の記録はありません')).not.toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('ページ送りで次のページを取り直し、最終ページでは先へ進めない', async () => {
    mockedList.mockImplementation(pagedServer(45));

    render(<MemberVisitsPage />);

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    expect(await screen.findByText('店舗20')).toBeInTheDocument();
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(5));

    expect(mockedList.mock.calls.map(([number]) => number)).toEqual([0, 1, 2]);
    expect(screen.getAllByRole('button', { name: '次へ' })[0]).toBeDisabled();
  });

  it('来店が無ければ空を名乗る', async () => {
    render(<MemberVisitsPage />);

    expect(await screen.findByText('まだ来店の記録はありません')).toBeInTheDocument();
    // 申領の画面への入口は伝票の QR だけなので、来店が並ぶ道筋を空の区画で示す
    expect(screen.getByText(/伝票の QR を読み取ると/)).toBeInTheDocument();
  });
});
