import { StrictMode } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ShiftsPage from '../ShiftsPage';
import { castApi } from '@/entities/cast';
import { shiftApi } from '@/entities/shift';
import { toDateStr } from '../../lib/datetime';

jest.mock('@/entities/cast', () => ({
  castApi: { list: jest.fn() },
}));

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    list: jest.fn(),
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

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が名簿をクリアする
  // 以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  // （シフト側の同じ守衛は effect 内の ignore が既に担っている）
  it('二重 mount で古い失敗が後から着いても、名簿を消さないこと', async () => {
    mockedShiftList.mockResolvedValue([
      {
        id: 'sh1',
        cast_id: 'cast-1',
        work_date: toDateStr(new Date()),
        start_time: '18:00:00',
        end_time: '23:00:00',
      },
    ]);
    let failStale = (): void => {};
    mockedCastList
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue(castPage([{ id: 'cast-1', name: 'キャストA' }]));

    render(
      <StrictMode>
        <ShiftsPage />
      </StrictMode>
    );

    // タイムラインの名前解決は名簿を読む。名簿が消えると「不明」に化ける
    fireEvent.click(await screen.findByRole('button', { name: String(DAY_IN_MONTH) }));
    expect(await screen.findByText('キャストA')).toBeInTheDocument();

    await act(async () => {
      failStale();
    });

    expect(screen.getByText('キャストA')).toBeInTheDocument();
    expect(screen.queryByText('キャストの取得に失敗しました')).not.toBeInTheDocument();
  });
});
