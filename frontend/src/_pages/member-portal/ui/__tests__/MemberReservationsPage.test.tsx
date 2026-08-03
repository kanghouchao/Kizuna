import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationsPage } from '../MemberReservationsPage';
import { memberOrderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  memberOrderApi: { list: jest.fn(), cancel: jest.fn() },
}));

const mockedList = memberOrderApi.list as jest.Mock;
const mockedCancel = memberOrderApi.cancel as jest.Mock;

const page = (rows: unknown[]) => ({ rows, page: 0, pageCount: 1, total: rows.length });

describe('MemberReservationsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('全店舗の予約を状態つきで表示する', async () => {
    mockedList.mockResolvedValue(
      page([
        { id: 'o1', store_name: '店舗A', business_date: '2026-08-10', pax: 2, status: 'CREATED' },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          cast_name: 'さくら',
          status: 'CONFIRMED',
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
        { id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' },
        { id: 'o2', store_name: '店舗B', business_date: '2026-08-11', status: 'CONFIRMED' },
      ])
    );

    render(<MemberReservationsPage />);

    await screen.findByText('店舗A');
    expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(1);
  });

  it('取り下げると本人取り下げの API を呼び、一覧を取り直す', async () => {
    mockedList.mockResolvedValue(
      page([{ id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' }])
    );
    mockedCancel.mockResolvedValue({});

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() => expect(mockedCancel).toHaveBeenCalledWith('o1'));
    expect(mockedList).toHaveBeenCalledTimes(2);
  });

  it('取得に失敗したらエラーメッセージを表示する', async () => {
    mockedList.mockRejectedValue(new Error('failed'));

    render(<MemberReservationsPage />);

    expect(
      await screen.findByText('予約を取得できませんでした。再読み込みしてください。')
    ).toBeInTheDocument();
  });
});
