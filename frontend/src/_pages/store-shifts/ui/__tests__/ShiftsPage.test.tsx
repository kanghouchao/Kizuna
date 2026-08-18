import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ShiftsPage from '../ShiftsPage';
import { castApi } from '@/entities/cast';
import { shiftApi } from '@/entities/shift';
import { addDaysStr, toDateStr } from '../../lib/datetime';

jest.mock('@/entities/cast', () => ({
  castApi: { list: jest.fn() },
}));

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    list: jest.fn(),
    create: jest.fn(),
    listShiftRequests: jest.fn(),
    approveShiftRequest: jest.fn(),
    declineShiftRequest: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedCastList = castApi.list as jest.Mock;
const mockedShiftList = shiftApi.list as jest.Mock;
const mockedShiftCreate = shiftApi.create as jest.Mock;

/** 月グリッドの先頭 6 セル・末尾 14 セルにしか他月は現れないため、15 日は常に当月で一意。 */
const DAY_IN_MONTH = 15;

/** キャスト一覧の 1 ページ分。頁側は rows と「size 未満なら最終ページ」だけを読む。 */
const castPage = (rows: { id: string; name: string }[] = []) => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
});

describe('ShiftsPage のタブ遷移', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastList.mockResolvedValue(castPage());
    mockedShiftList.mockResolvedValue([]);
  });

  it('カレンダーの日付をクリックするとタイムラインタブへ切り替わり、その日を表示すること', async () => {
    render(<ShiftsPage />);

    const day = await screen.findByRole('button', { name: String(DAY_IN_MONTH) });
    fireEvent.click(day);

    const now = new Date();
    const expected = toDateStr(new Date(now.getFullYear(), now.getMonth(), DAY_IN_MONTH));

    await waitFor(() =>
      expect(screen.getByRole('tab', { name: 'タイムライン' })).toHaveAttribute(
        'aria-selected',
        'true'
      )
    );
    expect(screen.getByRole('tab', { name: 'カレンダー' })).toHaveAttribute(
      'aria-selected',
      'false'
    );
    expect(await screen.findByText(`${expected} の出勤`)).toBeInTheDocument();
  });

  it('月を切り替えると新しい区間で取り直すこと', async () => {
    render(<ShiftsPage />);
    await waitFor(() => expect(mockedShiftList).toHaveBeenCalledTimes(1));
    const firstFrom = mockedShiftList.mock.calls[0][0].from;

    fireEvent.click(screen.getByRole('button', { name: '前の月' }));

    await waitFor(() => expect(mockedShiftList).toHaveBeenCalledTimes(2));
    expect(mockedShiftList.mock.calls[1][0].from < firstFrom).toBe(true);
  });

  it('保存後はシフトを取り直すこと', async () => {
    // 保存の反映は再取得でしか起きない。配線が切れても保存自体は成功するので症状が出ない
    mockedCastList.mockResolvedValue(castPage([{ id: 'c1', name: 'さくら' }]));
    mockedShiftCreate.mockResolvedValue({});
    render(<ShiftsPage />);

    fireEvent.click(await screen.findByRole('button', { name: String(DAY_IN_MONTH) }));
    fireEvent.click(await screen.findByRole('button', { name: 'シフト追加' }));

    const dialog = await screen.findByRole('dialog');
    const callsBeforeSave = mockedShiftList.mock.calls.length;
    fireEvent.click(within(dialog).getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedShiftCreate).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockedShiftList.mock.calls.length).toBeGreaterThan(callsBeforeSave));
  });
});

describe('タイムラインの日付ナビゲーション', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastList.mockResolvedValue(castPage());
    mockedShiftList.mockResolvedValue([]);
  });

  const openTimeline = async () => {
    render(<ShiftsPage />);
    fireEvent.click(screen.getByRole('tab', { name: 'タイムライン' }));
    const today = toDateStr(new Date());
    expect(await screen.findByText(`${today} の出勤`)).toBeInTheDocument();
    return today;
  };

  it('翌日ボタンで表示日が進み、その日の区間で取り直すこと', async () => {
    const today = await openTimeline();
    const callsBefore = mockedShiftList.mock.calls.length;

    fireEvent.click(screen.getByRole('button', { name: '翌日' }));

    const tomorrow = addDaysStr(today, 1);
    expect(await screen.findByText(`${tomorrow} の出勤`)).toBeInTheDocument();
    await waitFor(() => expect(mockedShiftList.mock.calls.length).toBeGreaterThan(callsBefore));
    expect(mockedShiftList.mock.calls.at(-1)?.[0]).toEqual({ from: tomorrow, to: tomorrow });
  });

  it('クイックボタンは選択中の日ではなく実際の今日を基準にジャンプすること', async () => {
    const today = await openTimeline();

    // 先に前日へ動かしてから明後日を押す — 選択中の日基準なら today+1 に化ける
    fireEvent.click(screen.getByRole('button', { name: '前日' }));
    expect(await screen.findByText(`${addDaysStr(today, -1)} の出勤`)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '明後日' }));
    expect(await screen.findByText(`${addDaysStr(today, 2)} の出勤`)).toBeInTheDocument();
  });

  it('日付入力で任意の日へ切り替わり、その日の区間で取り直すこと', async () => {
    await openTimeline();

    fireEvent.change(screen.getByLabelText('表示する日付'), { target: { value: '2031-01-05' } });

    expect(await screen.findByText('2031-01-05 の出勤')).toBeInTheDocument();
    await waitFor(() =>
      expect(mockedShiftList.mock.calls.at(-1)?.[0]).toEqual({
        from: '2031-01-05',
        to: '2031-01-05',
      })
    );
  });
});

describe('ShiftsPage の取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastList.mockResolvedValue(castPage());
    mockedShiftList.mockResolvedValue([]);
  });

  it('シフトが取れなければカレンダーを出さず、その場所が失敗を名乗って再試行できること', async () => {
    mockedShiftList.mockRejectedValueOnce(new Error('boom'));

    render(<ShiftsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('シフトの取得に失敗しました')).toBeInTheDocument();
    // 空のカレンダーは「この月は誰も出勤しない」と読める
    expect(screen.queryByRole('button', { name: String(DAY_IN_MONTH) })).not.toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByRole('button', { name: String(DAY_IN_MONTH) })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('キャスト名簿が取れなければ頁の高さで失敗を名乗り、再試行できること', async () => {
    mockedCastList.mockRejectedValueOnce(new Error('boom'));

    render(<ShiftsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('キャストの取得に失敗しました')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });
});
