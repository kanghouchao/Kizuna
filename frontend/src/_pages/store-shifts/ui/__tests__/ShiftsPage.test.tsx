import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ShiftsPage from '../ShiftsPage';
import { castApi } from '@/entities/cast';
import { shiftApi } from '@/entities/shift';
import { notify } from '@/shared/notify';
import { addDaysStr, toDateStr } from '../../lib/datetime';

jest.mock('@/entities/cast', () => ({
  castApi: { list: jest.fn() },
}));

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    list: jest.fn(),
    create: jest.fn(),
    changePublication: jest.fn(),
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
const mockedChangePublication = shiftApi.changePublication as jest.Mock;

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

  it('取得に失敗しても日付ナビは残り、別の日へ動けば取り直して復旧すること', async () => {
    const today = await openTimeline();
    mockedShiftList.mockRejectedValueOnce(new Error('boom'));

    fireEvent.click(screen.getByRole('button', { name: '翌日' }));

    // 本体だけが失敗を名乗り、ヘッダのナビは操作可能なまま
    const region = await screen.findByRole('alert');
    expect(within(region).getByText('シフトの取得に失敗しました')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '翌日' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '翌日' }));

    expect(await screen.findByText(`${addDaysStr(today, 2)} の出勤`)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
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

describe('シフトの公開可否', () => {
  const TODAY = toDateStr(new Date());
  const SAKURA = { id: 'c1', name: 'さくら' };
  const AOI = { id: 'c2', name: 'あおい' };

  /** 公開中の確定シフト（さくら 18:00–23:00）。 */
  const shown = {
    id: 's1',
    cast_id: 'c1',
    work_date: TODAY,
    start_time: '18:00:00',
    end_time: '23:00:00',
    status: 'CONFIRMED',
    published: true,
  };
  /** 非公開の確定シフト（あおい 19:00–22:00）。 */
  const hidden = {
    ...shown,
    id: 's2',
    cast_id: 'c2',
    start_time: '19:00:00',
    end_time: '22:00:00',
    published: false,
  };
  /** 仮シフト（あおい 20:00–21:00）。公開の操作面を持たない。 */
  const tentative = {
    ...shown,
    id: 's3',
    cast_id: 'c2',
    start_time: '20:00:00',
    end_time: '21:00:00',
    status: 'TENTATIVE',
    published: false,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastList.mockResolvedValue(castPage([SAKURA, AOI]));
  });

  const openTimeline = async () => {
    render(<ShiftsPage />);
    fireEvent.click(screen.getByRole('tab', { name: 'タイムライン' }));
    expect(await screen.findByText(`${TODAY} の出勤`)).toBeInTheDocument();
  };

  it('バーの目玉で個別に切り替わり、応答がその場に反映されること', async () => {
    mockedShiftList.mockResolvedValue([shown]);
    mockedChangePublication.mockResolvedValue({ ...shown, published: false });
    await openTimeline();

    fireEvent.click(
      await screen.findByRole('button', { name: 'さくら 18:00–23:00 を非公開にする' })
    );

    await waitFor(() => expect(mockedChangePublication).toHaveBeenCalledWith('s1', false));
    // 取り直しではなく応答の差し替えで反映する — 目玉が裏返り、押し直せば戻せる
    expect(
      await screen.findByRole('button', { name: 'さくら 18:00–23:00 を公開する' })
    ).toBeInTheDocument();
  });

  it('日単位一括は必要な行だけを逐行で切り替えること', async () => {
    mockedShiftList.mockResolvedValue([shown, hidden]);
    mockedChangePublication.mockImplementation(async (id: string) => ({
      ...(id === 's1' ? shown : hidden),
      published: true,
    }));
    await openTimeline();

    fireEvent.click(await screen.findByRole('button', { name: '全て公開' }));

    // 既に公開中の s1 まで送ると、一括 API の無い逐行呼びが無用に倍化する
    await waitFor(() => expect(mockedChangePublication).toHaveBeenCalledTimes(1));
    expect(mockedChangePublication).toHaveBeenCalledWith('s2', true);
  });

  it('非公開のシフトが破線中抜きバーとパネルの件数で区別されること', async () => {
    mockedShiftList.mockResolvedValue([shown, hidden]);
    await openTimeline();

    const bar = (await screen.findByRole('button', { name: 'あおい 19:00–22:00 を編集' }))
      .parentElement;
    expect(bar).toHaveClass('border-dashed');
    const shownBar = screen.getByRole('button', {
      name: 'さくら 18:00–23:00 を編集',
    }).parentElement;
    expect(shownBar).not.toHaveClass('border-dashed');

    expect(screen.getByText('非公開 1件')).toBeInTheDocument();
    expect(screen.getByText('公開 1件')).toBeInTheDocument();
  });

  it('カレンダーの日セルに非公開の件数が出ること', async () => {
    mockedShiftList.mockResolvedValue([shown, hidden]);
    render(<ShiftsPage />);

    expect(await screen.findByText('非公開1')).toBeInTheDocument();
  });

  it('仮シフトには公開の操作面が現れないこと', async () => {
    mockedShiftList.mockResolvedValue([shown, tentative]);
    await openTimeline();

    // 仮シフトはフラグ値に関わらず店外へ出ない。切替を出すと「公開した」と読める操作が
    // 効かないまま残る（後端は TENTATIVE への切替を拒まない — 守っているのはここだけ）
    expect(
      await screen.findByRole('button', { name: 'あおい 20:00–21:00 を編集' })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /あおい 20:00–21:00 を(非)?公開/ })
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: /あおい 20:00–21:00/ })).not.toBeInTheDocument();
    expect(screen.getByText('確定すると公開できます')).toBeInTheDocument();
  });

  it('一括の途中で落ちた行があっても、通った行だけが切り替わること', async () => {
    mockedShiftList.mockResolvedValue([
      shown,
      { ...shown, id: 's4', cast_id: 'c2', start_time: '19:00:00', end_time: '22:00:00' },
    ]);
    mockedChangePublication.mockImplementation(async (id: string) =>
      id === 's1' ? { ...shown, published: false } : Promise.reject(new Error('boom'))
    );
    await openTimeline();

    fireEvent.click(await screen.findByRole('button', { name: '全て非公開' }));

    // 落ちた行まで裏返すと、画面が「隠した」と言いながら公式サイトには出続ける
    expect(
      await screen.findByRole('button', { name: 'さくら 18:00–23:00 を非公開にする' })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'あおい 19:00–22:00 を非公開にする' })
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('1件のシフトの公開状態を変更できませんでした')
    );
  });

  it('パネルの Switch も同じ切替の口へ入ること', async () => {
    mockedShiftList.mockResolvedValue([shown]);
    mockedChangePublication.mockResolvedValue({ ...shown, published: false });
    await openTimeline();

    fireEvent.click(await screen.findByRole('switch', { name: 'さくら 18:00–23:00 を公開する' }));

    await waitFor(() => expect(mockedChangePublication).toHaveBeenCalledWith('s1', false));
  });
});
