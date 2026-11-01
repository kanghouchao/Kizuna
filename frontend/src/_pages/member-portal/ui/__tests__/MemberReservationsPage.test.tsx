import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationsPage } from '../MemberReservationsPage';
import { memberOrderApplicationApi } from '@/entities/order';
import { notify } from '@/shared/notify';
import { ClientDataError } from '@/shared/lib';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  memberOrderApplicationApi: { list: jest.fn(), withdraw: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedList = memberOrderApplicationApi.list as jest.Mock;
const mockedWithdraw = memberOrderApplicationApi.withdraw as jest.Mock;

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
    id: `o${start + i}`,
    store_name: `店舗${start + i}`,
    business_date: '2026-08-10',
    status: 'PENDING',
    expired: false,
  }));
  return Promise.resolve(page(rows, number, total));
};

describe('MemberReservationsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('全店舗の予約を状態つきで表示する', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'o1',
          store_name: '店舗A',
          business_date: '2026-08-10',
          pax: 2,
          status: 'PENDING',
          expired: false,
        },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          cast_name: 'さくら',
          status: 'CONFIRMED',
          expired: false,
        },
      ])
    );

    render(<MemberReservationsPage />);

    expect(await screen.findByText('店舗A')).toBeInTheDocument();
    expect(screen.getByText('店舗B')).toBeInTheDocument();
    expect(screen.getByText('申請中')).toBeInTheDocument();
    expect(screen.getByText('確定')).toBeInTheDocument();
  });

  it('取り下げボタンは確定前の予約にだけ出す', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'o1',
          store_name: '店舗A',
          business_date: '2026-08-10',
          status: 'PENDING',
          expired: false,
        },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          status: 'CONFIRMED',
          expired: false,
        },
      ])
    );

    render(<MemberReservationsPage />);

    await screen.findByText('店舗A');
    expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(1);
  });

  it('取り下げると本人取り下げの API を呼び、表示中のページを取り直す', async () => {
    const pending = {
      id: 'o1',
      store_name: '店舗A',
      business_date: '2026-08-10',
      status: 'PENDING',
      expired: false,
    };
    mockedList
      .mockResolvedValueOnce(page([pending]))
      .mockResolvedValueOnce(page([{ ...pending, status: 'WITHDRAWN' }]));
    mockedWithdraw.mockResolvedValue({ ...pending, status: 'WITHDRAWN' });

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() => expect(mockedWithdraw).toHaveBeenCalledWith('o1'));
    expect(await screen.findByText('取り下げ')).toBeInTheDocument();
    expect(mockedList).toHaveBeenLastCalledWith(0);
    expect(mockedList).toHaveBeenCalledTimes(2);
  });

  it('識別子を欠いた失敗は、汎用文言に潰さずそのまま名乗ること', async () => {
    // 識別子を欠いた要求はアダプタが組む前に止める（order-api.test.ts）。画面が負うのは
    // その失敗を「取り下げに失敗しました」へ潰さないこと
    mockedList.mockResolvedValue(
      page([
        { store_name: '店舗A', business_date: '2026-08-10', status: 'PENDING', expired: false },
      ])
    );
    mockedWithdraw.mockRejectedValue(
      new ClientDataError('予約申請の識別子が取得できていません。画面を読み直してください')
    );

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        expect.stringContaining('予約申請の識別子が取得できていません')
      )
    );
  });

  it('取得に失敗したら領域エラー態を出し、再試行で復帰できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberReservationsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    expect(screen.queryByText('予約はまだありません。')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('次ページの取得に失敗したら領域ごとエラー態になり、再試行は先頭から取り直す', async () => {
    // 古い行を残すと、読めなかった一覧に前回の内容が居座って「これが最新」に見える。
    const server = pagedServer(25);
    mockedList.mockImplementation(server);

    render(<MemberReservationsPage />);
    expect(await screen.findByText('店舗0')).toBeInTheDocument();

    mockedList.mockImplementation((number: number) =>
      number > 0 ? Promise.reject(new Error('failed')) : server(number)
    );
    fireEvent.click(screen.getByRole('button', { name: '次のページ' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '取り下げる' })).toHaveLength(0);

    mockedList.mockImplementation(server);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith(0));
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(20)
    );
  });

  it('ページ送りで次のページを取り直し、最終ページでは先へ進めない', async () => {
    mockedList.mockImplementation(pagedServer(45));

    render(<MemberReservationsPage />);

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
    expect(await screen.findByText('店舗20')).toBeInTheDocument();
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '次のページ' }));
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(5)
    );

    expect(mockedList.mock.calls.map(([number]) => number)).toEqual([0, 1, 2]);
    expect(screen.getByRole('button', { name: '次のページ' })).toBeDisabled();
  });
});
