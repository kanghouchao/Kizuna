import { act, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CastSchedulePage } from '../CastSchedulePage';
import { shiftApi } from '@/entities/shift';
import { notify } from '@/shared/notify';

jest.mock('@/entities/shift', () => ({
  shiftApi: { mySchedule: jest.fn(), myStores: jest.fn(), submitShiftChangeRequest: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedNotifyError = notify.error as jest.Mock;
const mockedMySchedule = shiftApi.mySchedule as jest.Mock;
const mockedMyStores = shiftApi.myStores as jest.Mock;
const mockedSubmitChange = shiftApi.submitShiftChangeRequest as jest.Mock;

function daysBetween(fromStr: string, toStr: string): number {
  const from = new Date(fromStr);
  const to = new Date(toStr);
  return Math.round((to.getTime() - from.getTime()) / (24 * 60 * 60 * 1000));
}

describe('CastSchedulePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMyStores.mockResolvedValue([{ store_id: 1, store_name: '店舗A' }]);
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

    expect(await screen.findByText('この週の出勤予定はありません')).toBeInTheDocument();
  });

  it('取得に失敗した場合はエラー文言を表示し、空状態文言とは区別する', async () => {
    mockedMySchedule.mockRejectedValueOnce(new Error('network error'));
    mockedMySchedule.mockResolvedValueOnce([]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('スケジュールの取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('この週の出勤予定はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('この週の出勤予定はありません')).toBeInTheDocument();
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
    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
  });

  it('日付ごとに見出しを立て、各行に時間帯と店舗名を出すこと', async () => {
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
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('18:00〜20:00')).toBeInTheDocument();
    // 年は実行日の年と比べて付くかが決まるため、月日と曜日だけを断言する
    const headings = screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent);
    expect(headings).toEqual([
      expect.stringMatching(/7\/20（月）$/),
      expect.stringMatching(/7\/22（水）$/),
    ]);
    const rows = screen.getAllByRole('listitem').map(li => li.textContent);
    expect(rows).toEqual([
      expect.stringMatching(/18:00〜20:00.*店舗A/),
      expect.stringMatching(/10:00〜12:00.*店舗A/),
    ]);
  });

  it('日をまたぐ予定は終了に「翌」を付けること', async () => {
    mockedMySchedule.mockResolvedValue([
      {
        work_date: '2026-07-20',
        start_time: '22:00:00',
        end_time: '02:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    render(<CastSchedulePage />);

    expect(await screen.findByText('22:00〜翌2:00')).toBeInTheDocument();
  });

  describe('店舗チップ', () => {
    const twoStoreWeek = [
      {
        id: 'sh1',
        work_date: '2999-07-20',
        start_time: '18:00:00',
        end_time: '20:00:00',
        status: 'CONFIRMED',
        store_id: 1,
        store_name: '店舗A',
      },
      {
        id: 'sh2',
        work_date: '2999-07-21',
        start_time: '10:00:00',
        end_time: '12:00:00',
        status: 'CONFIRMED',
        store_id: 2,
        store_name: '店舗B',
      },
    ];

    it('複数店舗なら先頭の店舗の予定だけを出し、チップで店舗を切り替えること', async () => {
      mockedMyStores.mockResolvedValue([
        { store_id: 1, store_name: '店舗A' },
        { store_id: 2, store_name: '店舗B' },
      ]);
      mockedMySchedule.mockResolvedValue(twoStoreWeek);

      render(<CastSchedulePage />);

      expect(await screen.findByText('18:00〜20:00')).toBeInTheDocument();
      expect(screen.getByRole('group', { name: '店舗' })).toBeInTheDocument();
      expect(screen.queryByText('10:00〜12:00')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: '店舗B' }));

      expect(await screen.findByText('10:00〜12:00')).toBeInTheDocument();
      expect(screen.queryByText('18:00〜20:00')).not.toBeInTheDocument();
    });

    it('在籍店舗に無い店舗（退店済み）の予定もその週のチップから見られること', async () => {
      // 在籍店舗の一覧は退店済みを含まないが、予定は退店前に確定した分も返る
      mockedMyStores.mockResolvedValue([
        { store_id: 1, store_name: '店舗A' },
        { store_id: 2, store_name: '店舗B' },
      ]);
      mockedMySchedule.mockResolvedValue([
        ...twoStoreWeek,
        {
          id: 'sh3',
          work_date: '2999-07-22',
          start_time: '13:00:00',
          end_time: '15:00:00',
          status: 'CONFIRMED',
          store_id: 3,
          store_name: '旧店舗C',
        },
      ]);

      render(<CastSchedulePage />);
      expect(await screen.findByText('18:00〜20:00')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: '旧店舗C' }));

      expect(await screen.findByText('13:00〜15:00')).toBeInTheDocument();
      expect(screen.queryByText('18:00〜20:00')).not.toBeInTheDocument();
    });

    it('在籍 1 店舗でも、その週に退店済みの店舗の予定があればチップで分けること', async () => {
      mockedMySchedule.mockResolvedValue([
        twoStoreWeek[0],
        { ...twoStoreWeek[1], store_id: 3, store_name: '旧店舗C' },
      ]);

      render(<CastSchedulePage />);

      expect(await screen.findByRole('group', { name: '店舗' })).toBeInTheDocument();
      expect(screen.getByText('18:00〜20:00')).toBeInTheDocument();
      expect(screen.queryByText('10:00〜12:00')).not.toBeInTheDocument();
    });

    it('1 店舗でその週の予定もその店舗だけならチップを出さないこと', async () => {
      mockedMySchedule.mockResolvedValue([twoStoreWeek[0]]);

      render(<CastSchedulePage />);

      expect(await screen.findByText('18:00〜20:00')).toBeInTheDocument();
      expect(screen.queryByRole('group', { name: '店舗' })).not.toBeInTheDocument();
    });

    it('店舗を読み終えるまでは予定の行を出さないこと', async () => {
      // 店舗が決まる前に行を出すと、複数店舗のキャストに他店の予定が一瞬混ざって見える
      mockedMyStores.mockReturnValue(new Promise(() => {}));
      mockedMySchedule.mockResolvedValue(twoStoreWeek);

      render(<CastSchedulePage />);

      await waitFor(() => expect(mockedMySchedule).toHaveBeenCalled());
      // 予定の取得を解決させ切ってから観測する
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0));
      });
      expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
      expect(screen.queryByText('18:00〜20:00')).not.toBeInTheDocument();
    });

    it('店舗の取得に失敗したらその区画に再試行を出し、予定は絞らずに出すこと', async () => {
      mockedMyStores.mockRejectedValueOnce(new Error('network error'));
      mockedMySchedule.mockResolvedValue(twoStoreWeek);

      render(<CastSchedulePage />);

      expect(await screen.findByText('店舗の取得に失敗しました')).toBeInTheDocument();
      expect(screen.getByText('18:00〜20:00')).toBeInTheDocument();
      expect(screen.getByText('10:00〜12:00')).toBeInTheDocument();

      mockedMyStores.mockResolvedValue([
        { store_id: 1, store_name: '店舗A' },
        { store_id: 2, store_name: '店舗B' },
      ]);
      fireEvent.click(screen.getByRole('button', { name: '再試行' }));

      expect(await screen.findByRole('group', { name: '店舗' })).toBeInTheDocument();
      expect(screen.queryByText('10:00〜12:00')).not.toBeInTheDocument();
    });
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

  it('変更申請ボタンで下部シートが開き、現行の予定を名乗って日時を初期値に入れること', async () => {
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

    const sheet = await screen.findByRole('dialog', { name: 'シフトの変更申請' });
    expect(sheet).toHaveAccessibleDescription('現在: 2999/7/20（土）18:00〜20:00・店舗A');
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

  it('「やめる」で提出せずに下部シートを閉じること', async () => {
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
    await screen.findByRole('dialog', { name: 'シフトの変更申請' });

    fireEvent.click(screen.getByRole('button', { name: 'やめる' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockedSubmitChange).not.toHaveBeenCalled();
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
