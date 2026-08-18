import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CastSchedulePage } from '../CastSchedulePage';
import { shiftApi } from '@/entities/shift';
import { notify } from '@/shared/notify';

jest.mock('@/entities/shift', () => ({
  shiftApi: { mySchedule: jest.fn(), submitShiftChangeRequest: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedNotifyError = notify.error as jest.Mock;
const mockedMySchedule = shiftApi.mySchedule as jest.Mock;
const mockedSubmitChange = shiftApi.submitShiftChangeRequest as jest.Mock;

function daysBetween(fromStr: string, toStr: string): number {
  const from = new Date(fromStr);
  const to = new Date(toStr);
  return Math.round((to.getTime() - from.getTime()) / (24 * 60 * 60 * 1000));
}

describe('CastSchedulePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('mount 時に日曜始まり7日分の範囲で mySchedule を呼ぶ', async () => {
    mockedMySchedule.mockResolvedValue([]);

    render(<CastSchedulePage />);

    await waitFor(() => expect(mockedMySchedule).toHaveBeenCalledTimes(1));
    const { from, to } = mockedMySchedule.mock.calls[0][0];
    expect(new Date(from).getDay()).toBe(0);
    expect(daysBetween(from, to)).toBe(6);
  });

  it('確定シフトが無い週は空状態文言を表示する', async () => {
    mockedMySchedule.mockResolvedValue([]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('今週の確定シフトはありません')).toBeInTheDocument();
  });

  it('取得に失敗した場合はエラー文言を表示し、空状態文言とは区別する', async () => {
    mockedMySchedule.mockRejectedValueOnce(new Error('network error'));
    mockedMySchedule.mockResolvedValueOnce([]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('スケジュールの取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('今週の確定シフトはありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('今週の確定シフトはありません')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('週を送っている間は前の週の行を残さない', async () => {
    // 見出しの範囲は即座に新しい週へ動くため、行だけ前の週のまま残ると別の週の予定に読める
    mockedMySchedule.mockResolvedValueOnce([
      {
        work_date: '2026-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    render(<CastSchedulePage />);
    expect(await screen.findByText('店舗A')).toBeInTheDocument();

    // 次週の取得は解決させない（取得中の相を保ったまま観測する）
    mockedMySchedule.mockReturnValueOnce(new Promise(() => {}));
    fireEvent.click(screen.getByRole('button', { name: '次週' }));

    await waitFor(() => expect(screen.queryByText('店舗A')).not.toBeInTheDocument());
    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('日付ごとにグルーピングし、店舗チップと時間帯を表示する', async () => {
    mockedMySchedule.mockResolvedValue([
      {
        work_date: '2026-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
      {
        work_date: '2026-07-22',
        start_time: '10:00:00',
        end_time: '12:00:00',
        status: 'CONFIRMED',
        store_id: 2,
        store_name: '店舗B',
      },
    ]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('店舗A')).toBeInTheDocument();
    expect(screen.getByText('18:00–20:00')).toBeInTheDocument();
    expect(screen.getByText('店舗B')).toBeInTheDocument();
    expect(screen.getByText('10:00–12:00')).toBeInTheDocument();
  });

  it('次週ボタンで7日後の範囲を再取得する', async () => {
    mockedMySchedule.mockResolvedValue([]);

    render(<CastSchedulePage />);
    await waitFor(() => expect(mockedMySchedule).toHaveBeenCalledTimes(1));
    const firstFrom = mockedMySchedule.mock.calls[0][0].from;

    fireEvent.click(screen.getByRole('button', { name: '次週' }));

    await waitFor(() => expect(mockedMySchedule).toHaveBeenCalledTimes(2));
    const secondFrom = mockedMySchedule.mock.calls[1][0].from;
    expect(daysBetween(firstFrom, secondFrom)).toBe(7);
  });

  it('変更申請ボタンでモーダルが開き、現行の日時が初期値に入ること', async () => {
    mockedMySchedule.mockResolvedValue([
      {
        id: 'sh1',
        work_date: '2999-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    render(<CastSchedulePage />);

    fireEvent.click(await screen.findByRole('button', { name: '変更申請' }));

    expect(await screen.findByText('シフトの変更申請')).toBeInTheDocument();
    expect(screen.getByLabelText('日付')).toHaveValue('2999-07-20');
    expect(screen.getByLabelText('開始')).toHaveValue('18:00');
    expect(screen.getByLabelText('終了')).toHaveValue('20:00');
  });

  it('変更申請の提出で shift_id と秒付き時刻の payload を送ること', async () => {
    mockedMySchedule.mockResolvedValue([
      {
        id: 'sh1',
        work_date: '2999-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);
    mockedSubmitChange.mockResolvedValue({ id: 'sr2', type: 'CHANGE', status: 'PENDING' });

    render(<CastSchedulePage />);
    fireEvent.click(await screen.findByRole('button', { name: '変更申請' }));
    await screen.findByText('シフトの変更申請');

    fireEvent.change(screen.getByLabelText('開始'), { target: { value: '19:00' } });
    fireEvent.click(screen.getByRole('button', { name: '変更を申請する' }));

    await waitFor(() =>
      expect(mockedSubmitChange).toHaveBeenCalledWith({
        shift_id: 'sh1',
        work_date: '2999-07-20',
        start_time: '19:00:00',
        end_time: '20:00:00',
        note: undefined,
      })
    );
  });

  it('変更申請が拒否されたらサーバの文言をそのまま出すこと', async () => {
    // 受理できる日付の下限は営業日で決まり、その境界を知るのはサーバだけ。提出フォームは日付を判定せず、
    // 拒否の理由はサーバの文言で伝える（提出ページ側と同じ規則）。
    mockedMySchedule.mockResolvedValue([
      {
        id: 'sh1',
        work_date: '2999-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);
    mockedSubmitChange.mockRejectedValue({
      response: { status: 400, data: { error: '勤務日は本日以降を指定してください' } },
    });

    render(<CastSchedulePage />);
    fireEvent.click(await screen.findByRole('button', { name: '変更申請' }));
    await screen.findByText('シフトの変更申請');

    fireEvent.change(screen.getByLabelText('日付'), { target: { value: '2000-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: '変更を申請する' }));

    await waitFor(() =>
      expect(mockedNotifyError).toHaveBeenCalledWith('勤務日は本日以降を指定してください')
    );
  });

  it('id を持たないシフトには変更申請ボタンを出さないこと', async () => {
    mockedMySchedule.mockResolvedValue([
      {
        work_date: '2999-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('店舗A')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '変更申請' })).not.toBeInTheDocument();
  });

  it('前週ボタンで7日前の範囲を再取得する', async () => {
    mockedMySchedule.mockResolvedValue([]);

    render(<CastSchedulePage />);
    await waitFor(() => expect(mockedMySchedule).toHaveBeenCalledTimes(1));
    const firstFrom = mockedMySchedule.mock.calls[0][0].from;

    fireEvent.click(screen.getByRole('button', { name: '前週' }));

    await waitFor(() => expect(mockedMySchedule).toHaveBeenCalledTimes(2));
    const secondFrom = mockedMySchedule.mock.calls[1][0].from;
    expect(daysBetween(secondFrom, firstFrom)).toBe(7);
  });
});
