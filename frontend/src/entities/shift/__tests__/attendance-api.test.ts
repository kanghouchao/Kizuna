import { attendanceApi } from '@/entities/shift';
import { apiClient } from '@/shared/api';

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
