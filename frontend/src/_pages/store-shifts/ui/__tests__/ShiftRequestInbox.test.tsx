import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ShiftRequestInbox } from '../ShiftRequestInbox';
import { CastResponse } from '@/entities/cast';
import { shiftApi } from '@/entities/shift';

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    listShiftRequests: jest.fn(),
    approveShiftRequest: jest.fn(),
    declineShiftRequest: jest.fn(),
  },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedList = shiftApi.listShiftRequests as jest.Mock;
const mockedApprove = shiftApi.approveShiftRequest as jest.Mock;
const mockedDecline = shiftApi.declineShiftRequest as jest.Mock;

const CASTS: CastResponse[] = [
  {
    id: 'cast-1',
    name: 'キャストA',
    status: 'ACTIVE',
    invitation_status: 'NOT_INVITED',
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z',
  },
];

const REQUEST = {
  id: 'sr1',
  cast_id: 'cast-1',
  work_date: '2026-08-01',
  start_time: '18:00:00',
  end_time: '23:00:00',
  note: 'よろしくお願いします',
  status: 'PENDING' as const,
};

describe('ShiftRequestInbox', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('受付中の希望が無い場合は空状態文言を表示する', async () => {
    mockedList.mockResolvedValue([]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('受付中の出勤希望はありません')).toBeInTheDocument();
  });

  it('status=PENDING で一覧を取得し、cast 名・日時・備考を表示する', async () => {
    mockedList.mockResolvedValue([REQUEST]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('キャストA')).toBeInTheDocument();
    expect(screen.getByText('2026-08-01 18:00–23:00')).toBeInTheDocument();
    expect(screen.getByText('よろしくお願いします')).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledWith({ status: 'PENDING' });
  });

  it('承認すると一覧とシフトを再取得すること', async () => {
    mockedList.mockResolvedValue([REQUEST]);
    mockedApprove.mockResolvedValue({ ...REQUEST, status: 'APPROVED' });
    const onApproved = jest.fn();

    render(<ShiftRequestInbox casts={CASTS} onApproved={onApproved} />);
    await screen.findByText('キャストA');

    fireEvent.click(screen.getByRole('button', { name: '承認' }));

    await waitFor(() => expect(mockedApprove).toHaveBeenCalledWith('sr1'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
    expect(onApproved).toHaveBeenCalledTimes(1);
  });

  it('辞退すると一覧のみ再取得し、シフト再取得は呼ばないこと', async () => {
    mockedList.mockResolvedValue([REQUEST]);
    mockedDecline.mockResolvedValue({ ...REQUEST, status: 'DECLINED' });
    const onApproved = jest.fn();

    render(<ShiftRequestInbox casts={CASTS} onApproved={onApproved} />);
    await screen.findByText('キャストA');

    fireEvent.click(screen.getByRole('button', { name: '辞退' }));

    await waitFor(() => expect(mockedDecline).toHaveBeenCalledWith('sr1'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
    expect(onApproved).not.toHaveBeenCalled();
  });
});
