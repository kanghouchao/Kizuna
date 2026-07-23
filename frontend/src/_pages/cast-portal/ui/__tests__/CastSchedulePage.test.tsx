import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CastSchedulePage } from '../CastSchedulePage';
import { shiftApi } from '@/entities/shift';

jest.mock('@/entities/shift', () => ({
  shiftApi: { mySchedule: jest.fn() },
}));

const mockedMySchedule = shiftApi.mySchedule as jest.Mock;

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
