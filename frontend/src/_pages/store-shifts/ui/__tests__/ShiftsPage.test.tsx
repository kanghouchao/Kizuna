import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

const mockedCastList = castApi.list as jest.Mock;
const mockedShiftList = shiftApi.list as jest.Mock;

/** 月グリッドの先頭 6 セル・末尾 14 セルにしか他月は現れないため、15 日は常に当月で一意。 */
const DAY_IN_MONTH = 15;

describe('ShiftsPage のタブ遷移', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastList.mockResolvedValue({ content: [] });
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
