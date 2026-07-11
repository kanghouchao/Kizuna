import { render, screen } from '@testing-library/react';
import ScheduleSection from '../templates/_sections/ScheduleSection';
import { PublicShift } from '../model/types';

function makeShift(overrides: Partial<PublicShift> = {}): PublicShift {
  return {
    cast_id: '1',
    cast_name: 'Cast 1',
    start_time: '20:00:00',
    end_time: '02:00:00',
    ...overrides,
  };
}

describe('ScheduleSection', () => {
  it('シフトが空の場合は空状態の文言を表示すること', () => {
    render(<ScheduleSection shifts={[]} />);

    expect(screen.getByText('本日の出勤情報はありません')).toBeInTheDocument();
    expect(
      screen.getByText('出勤情報は準備中です。最新の情報はお電話でお問い合わせください。')
    ).toBeInTheDocument();
  });

  it('シフトがある場合はキャスト名と時間帯を表示すること', () => {
    const shifts = [
      makeShift({
        cast_id: '1',
        cast_name: 'Cast 1',
        start_time: '20:00:00',
        end_time: '02:00:00',
      }),
    ];

    render(<ScheduleSection shifts={shifts} />);

    expect(screen.getByRole('heading', { name: 'Cast 1' })).toBeInTheDocument();
    expect(screen.getByText('20:00–02:00')).toBeInTheDocument();
  });

  it('キャスト詳細ページへのリンクを持つこと', () => {
    const shifts = [makeShift({ cast_id: '42' })];

    render(<ScheduleSection shifts={shifts} />);

    expect(screen.getByRole('link')).toHaveAttribute('href', '/casts/42');
  });

  it('終了時刻が 00:00 の場合は 24:00 と表示すること（跨夜表記）', () => {
    const shifts = [makeShift({ start_time: '20:00:00', end_time: '00:00:00' })];

    render(<ScheduleSection shifts={shifts} />);

    expect(screen.getByText('20:00–24:00')).toBeInTheDocument();
  });

  it('秒なしの時刻文字列でも HH:mm 表記になること', () => {
    const shifts = [makeShift({ start_time: '18:00', end_time: '20:00' })];

    render(<ScheduleSection shifts={shifts} />);

    expect(screen.getByText('18:00–20:00')).toBeInTheDocument();
  });

  it('与えられた配列順のまま描画すること（並べ替えしない）', () => {
    const shifts = [
      makeShift({
        cast_id: '1',
        cast_name: 'Cast B',
        start_time: '21:00:00',
        end_time: '23:00:00',
      }),
      makeShift({
        cast_id: '2',
        cast_name: 'Cast A',
        start_time: '18:00:00',
        end_time: '20:00:00',
      }),
    ];

    render(<ScheduleSection shifts={shifts} />);

    const headings = screen.getAllByRole('heading').map(el => el.textContent);
    expect(headings).toEqual(['Cast B', 'Cast A']);
  });
});
