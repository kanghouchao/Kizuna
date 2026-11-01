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
  beforeEach(() => {
    jest.clearAllMocks();
    mockedList.mockResolvedValue(page([]));
  });

  it('全店舗の来店を新しい順に、日付・店舗・人数・担当・獲得ポイントで並べる', async () => {
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
          visited_on: '2026-08-10',
          store_name: '店舗A',
          pax: 2,
          cast_name: 'キャスト花子',
          granted_points: 80,
        },
      ])
    );

    render(<MemberVisitsPage />);

    const rows = await screen.findAllByRole('listitem');
    expect(within(rows[0]).getByText('2026-08-11')).toBeInTheDocument();
    expect(within(rows[0]).getByText('店舗B')).toBeInTheDocument();
    expect(within(rows[0]).getByText('3 名')).toBeInTheDocument();
    expect(within(rows[0]).getByText('担当: キャスト太郎')).toBeInTheDocument();
    expect(within(rows[0]).getByText('+120 pt')).toBeInTheDocument();
    expect(within(rows[1]).getByText('店舗A')).toBeInTheDocument();
    expect(within(rows[1]).getByText('+80 pt')).toBeInTheDocument();
  });

  it('担当の記録が無い来店も行として出す', async () => {
    // 指名も割り当ても無い受注では担当名が応答から消える。担当欄が無いだけで行は残る。
    mockedList.mockResolvedValue(
      page([{ visited_on: '2026-08-11', store_name: '店舗A', pax: 1, granted_points: 0 }])
    );

    render(<MemberVisitsPage />);

    const row = await screen.findByRole('listitem');
    expect(within(row).getByText('店舗A')).toBeInTheDocument();
    expect(within(row).getByText('+0 pt')).toBeInTheDocument();
    expect(within(row).queryByText(/担当:/)).not.toBeInTheDocument();
  });

  it('取得完了までは読み込み中を表示する', () => {
    mockedList.mockReturnValue(new Promise(() => {}));

    render(<MemberVisitsPage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('取得に失敗したら失敗を名乗り、再試行で復帰できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberVisitsPage />);

    const region = await screen.findByRole('alert');
    expect(region).toHaveTextContent('来店履歴を取得できませんでした。');
    expect(screen.queryByText('来店の記録はまだありません。')).not.toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('ページ送りで次のページを取り直し、最終ページでは先へ進めない', async () => {
    mockedList.mockImplementation(pagedServer(45));

    render(<MemberVisitsPage />);

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
    expect(await screen.findByText('店舗20')).toBeInTheDocument();
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(5));

    expect(mockedList.mock.calls.map(([number]) => number)).toEqual([0, 1, 2]);
    expect(screen.getByRole('button', { name: '次のページ' })).toBeDisabled();
  });

  it('来店が無ければ空を名乗る', async () => {
    render(<MemberVisitsPage />);

    expect(await screen.findByText('来店の記録はまだありません。')).toBeInTheDocument();
  });
});
