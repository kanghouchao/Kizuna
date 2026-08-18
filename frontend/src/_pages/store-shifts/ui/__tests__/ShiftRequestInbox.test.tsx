import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
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

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
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

const CHANGE_REQUEST = {
  id: 'sr2',
  cast_id: 'cast-1',
  work_date: '2026-08-02',
  start_time: '19:00:00',
  end_time: '22:00:00',
  type: 'CHANGE' as const,
  shift_id: 'sh1',
  status: 'PENDING' as const,
  current_work_date: '2026-08-01',
  current_start_time: '18:00:00',
  current_end_time: '23:00:00',
  approvable: true,
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

  it('取得に失敗したら空状態を装わず、一覧の場所が失敗を名乗って再試行できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('boom'));

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('出勤希望の取得に失敗しました')).toBeInTheDocument();
    // 「ありません」に化けると未処理の希望を見落とす
    expect(screen.queryByText('受付中の出勤希望はありません')).not.toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();

    mockedList.mockResolvedValue([REQUEST]);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('キャストA')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('status=PENDING で一覧を取得し、cast 名・日時・備考を表示する', async () => {
    mockedList.mockResolvedValue([REQUEST]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('キャストA')).toBeInTheDocument();
    expect(screen.getByText('2026-08-01 18:00–23:00')).toBeInTheDocument();
    expect(screen.getByText('よろしくお願いします')).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledWith({ status: 'PENDING' });
  });

  it('種別を区別表示する: 新規希望はそのまま、変更申請は現行→申請の日時と専用ボタン文言', async () => {
    mockedList.mockResolvedValue([REQUEST, CHANGE_REQUEST]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('新規希望')).toBeInTheDocument();
    expect(screen.getByText('変更申請')).toBeInTheDocument();
    expect(screen.getByText('現行: 2026-08-01 18:00–23:00')).toBeInTheDocument();
    expect(screen.getByText('申請: 2026-08-02 19:00–22:00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '謝絶' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '承認してシフト更新' })).toBeInTheDocument();
  });

  it('承認不能(approvable=false)の変更申請は承認ボタンを出さず、失効の説明と謝絶のみ表示する', async () => {
    mockedList.mockResolvedValue([{ ...CHANGE_REQUEST, approvable: false }]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('変更申請')).toBeInTheDocument();
    expect(
      screen.getByText(
        '対象の営業日が終了したか、対象のシフトが削除または変更されたため承認できません（謝絶のみ可能）'
      )
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '承認してシフト更新' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '謝絶' })).toBeInTheDocument();
  });

  it('承認不能(approvable=false)の新規希望も承認ボタンを出さず、営業日終了の説明と辞退のみ表示する', async () => {
    mockedList.mockResolvedValue([{ ...REQUEST, approvable: false }]);

    render(<ShiftRequestInbox casts={CASTS} onApproved={jest.fn()} />);

    expect(await screen.findByText('新規希望')).toBeInTheDocument();
    expect(
      screen.getByText('対象の営業日が終了したため承認できません（辞退のみ可能）')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '承認' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '辞退' })).toBeInTheDocument();
  });

  it('変更申請の承認は同じ承認 API を呼び、シフト再取得コールバックが走ること', async () => {
    mockedList.mockResolvedValue([CHANGE_REQUEST]);
    mockedApprove.mockResolvedValue({ ...CHANGE_REQUEST, status: 'APPROVED' });
    const onApproved = jest.fn();

    render(<ShiftRequestInbox casts={CASTS} onApproved={onApproved} />);
    await screen.findByText('変更申請');

    fireEvent.click(screen.getByRole('button', { name: '承認してシフト更新' }));

    await waitFor(() => expect(mockedApprove).toHaveBeenCalledWith('sr2'));
    await waitFor(() => expect(onApproved).toHaveBeenCalledTimes(1));
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
