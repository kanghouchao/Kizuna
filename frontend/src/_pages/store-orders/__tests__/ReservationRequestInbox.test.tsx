import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ReservationRequestInbox } from '../ui/ReservationRequestInbox';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: { list: jest.fn(), confirm: jest.fn(), decline: jest.fn() },
}));

const mockedList = orderApi.list as jest.Mock;
const mockedConfirm = orderApi.confirm as jest.Mock;
const mockedDecline = orderApi.decline as jest.Mock;

const page = (rows: unknown[]) => ({ rows, page: 0, pageCount: 1, total: rows.length });

describe('ReservationRequestInbox', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('会員からの未確定申請だけを表示する（店舗が起こした受注・確定済みは出さない）', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'web-pending',
          status: 'CREATED',
          reception_route: 'WEB',
          business_date: '2026-08-10',
          requester_member_code: '123456789012',
        },
        { id: 'phone-pending', status: 'CREATED', reception_route: 'PHONE' },
        { id: 'web-confirmed', status: 'CONFIRMED', reception_route: 'WEB' },
      ])
    );

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(1);
    expect(screen.getByText('会員コード: 123456789012')).toBeInTheDocument();
  });

  it('確定すると確定 API を呼び、一覧の再取得を促す', async () => {
    mockedList.mockResolvedValue(
      page([{ id: 'o1', status: 'CREATED', reception_route: 'WEB', business_date: '2026-08-10' }])
    );
    mockedConfirm.mockResolvedValue({});
    const onProcessed = jest.fn();

    render(<ReservationRequestInbox onProcessed={onProcessed} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalledWith('o1'));
    expect(onProcessed).toHaveBeenCalled();
  });

  it('謝絶すると謝絶 API を呼ぶ', async () => {
    mockedList.mockResolvedValue(
      page([{ id: 'o1', status: 'CREATED', reception_route: 'WEB', business_date: '2026-08-10' }])
    );
    mockedDecline.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '謝絶' }));

    await waitFor(() => expect(mockedDecline).toHaveBeenCalledWith('o1'));
  });

  it('未確定の申請が無ければその旨を表示する', async () => {
    mockedList.mockResolvedValue(page([]));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('未確定の予約申請はありません')).toBeInTheDocument();
  });
});
