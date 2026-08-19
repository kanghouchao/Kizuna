import { attendanceApi } from '@/entities/shift';
import { apiClient } from '@/shared/api';
import { ClientDataError } from '@/shared/lib';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('attendanceApi', () => {
  it('list は /store/attendances を営業日で GET する', async () => {
    expect(await attendanceApi.list({ business_date: '2026-08-19' })).toEqual({
      ok: true,
      url: '/store/attendances',
    });
    expect(apiClient.get).toHaveBeenCalledWith('/store/attendances', {
      params: { business_date: '2026-08-19' },
    });
  });

  it('record は /store/attendances を POST する', async () => {
    expect(
      await attendanceApi.record({
        cast_id: 'c1',
        shift_id: 's1',
        actual_start_at: '2026-08-19T18:05',
        actual_end_at: null,
        waiting_place: null,
      })
    ).toEqual({ ok: true, url: '/store/attendances' });
  });

  it('correct は /store/attendances/:id を PUT する', async () => {
    expect(
      await attendanceApi.correct('a1', {
        business_date: '2026-08-19',
        actual_start_at: '2026-08-19T18:05',
        actual_end_at: '2026-08-19T23:10',
        waiting_place: '控室',
      })
    ).toEqual({ ok: true, url: '/store/attendances/a1' });
  });

  it('cancel は /store/attendances/:id/cancellation へ理由を載せて POST する', async () => {
    // 理由は取消の経緯を辿れる唯一の根拠なので、口の側でも落とさない
    await attendanceApi.cancel('a1', '二重記録');
    expect(apiClient.post).toHaveBeenCalledWith('/store/attendances/a1/cancellation', {
      reason: '二重記録',
    });
  });

  it('listAbsences は /store/absences を営業日で GET する', async () => {
    expect(await attendanceApi.listAbsences({ business_date: '2026-08-19' })).toEqual({
      ok: true,
      url: '/store/absences',
    });
  });
});

/**
 * 識別子を欠いた呼び出しは要求そのものを組まない。応答 DTO の項目はすべて可選なので、画面側が
 * `?? ''` で素通しすると単数の操作が一覧の URI へ飛び、届いた先の 404/405 が操作の失敗と
 * 見分けられなくなる。守りはアダプタの内側にあり、画面ごとに書かない。
 */
describe('識別子を欠いた attendanceApi', () => {
  const calls: [string, () => Promise<unknown>, string][] = [
    [
      'correct',
      () =>
        attendanceApi.correct(undefined, {
          business_date: '2026-08-19',
          actual_start_at: '2026-08-19T19:00:00',
        }),
      '当日実績',
    ],
    ['cancel', () => attendanceApi.cancel(undefined, '誤記録'), '当日実績'],
  ];

  it.each(calls)('%s は要求を出さず、名乗る失敗を投げる', async (_name, call, label) => {
    jest.clearAllMocks();

    await expect(call()).rejects.toBeInstanceOf(ClientDataError);
    await expect(call()).rejects.toThrow(`${label}の識別子が取得できていません`);
    expect(apiClient.get).not.toHaveBeenCalled();
    expect(apiClient.post).not.toHaveBeenCalled();
    expect(apiClient.put).not.toHaveBeenCalled();
  });
});
