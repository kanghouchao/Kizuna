import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ReservationRequestInbox } from '../ui/ReservationRequestInbox';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: { listReservationRequests: jest.fn(), confirm: jest.fn(), decline: jest.fn() },
}));

const mockedList = orderApi.listReservationRequests as jest.Mock;
const mockedConfirm = orderApi.confirm as jest.Mock;
const mockedDecline = orderApi.decline as jest.Mock;

describe('ReservationRequestInbox', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('サーバ側で絞り込まれた申請を表示し、一覧の取得では代替しないこと', async () => {
    mockedList.mockResolvedValue([
      {
        id: 'web-pending',
        status: 'CREATED',
        reception_route: 'WEB',
        business_date: '2026-08-10',
        requester_member_code: '123456789012',
      },
    ]);

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(1);
    expect(screen.getByText('会員コード: 123456789012')).toBeInTheDocument();
    // 絞り込みはサーバ側の責務。ページ指定を渡していないことで、取得窓に依存していないことを示す。
    expect(mockedList).toHaveBeenCalledWith();
  });

  it('確定すると確定 API を呼び、一覧の再取得を促す', async () => {
    mockedList.mockResolvedValue([
      { id: 'o1', status: 'CREATED', reception_route: 'WEB', business_date: '2026-08-10' },
    ]);
    mockedConfirm.mockResolvedValue({});
    const onProcessed = jest.fn();

    render(<ReservationRequestInbox onProcessed={onProcessed} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalledWith('o1'));
    expect(onProcessed).toHaveBeenCalled();
  });

  it('謝絶すると謝絶 API を呼ぶ', async () => {
    mockedList.mockResolvedValue([
      { id: 'o1', status: 'CREATED', reception_route: 'WEB', business_date: '2026-08-10' },
    ]);
    mockedDecline.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '謝絶' }));

    await waitFor(() => expect(mockedDecline).toHaveBeenCalledWith('o1'));
  });

  it('未確定の申請が無ければその旨を表示する', async () => {
    mockedList.mockResolvedValue([]);

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('未確定の予約申請はありません')).toBeInTheDocument();
  });

  it('取得に失敗したら空表示ではなくエラーを出し、再読み込みで復帰できる', async () => {
    // 瞬断を「申請なし」に見せると、店舗が未処理の申請を見落とす
    mockedList.mockRejectedValueOnce(new Error('network'));
    mockedList.mockResolvedValueOnce([
      { id: 'o1', status: 'CREATED', reception_route: 'WEB', business_date: '2026-08-10' },
    ]);

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('予約申請を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByText('未確定の予約申請はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再読み込み' }));

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
  });
});
