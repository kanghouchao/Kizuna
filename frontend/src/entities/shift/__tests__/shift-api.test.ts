import { shiftApi } from '@/entities/shift';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('shiftApi', () => {
  it('list は /store/shifts を GET する', async () => {
    expect(await shiftApi.list({ from: '2026-07-01', to: '2026-07-31' })).toEqual({
      ok: true,
      url: '/store/shifts',
    });
  });
  it('create は /store/shifts を POST する', async () => {
    expect(
      await shiftApi.create({
        cast_id: 'c1',
        work_date: '2026-07-08',
        start_time: '18:00:00',
        end_time: '23:00:00',
      })
    ).toEqual({ ok: true, url: '/store/shifts' });
  });
  it('update は /store/shifts/:id を PUT する', async () => {
    expect(await shiftApi.update('s1', { status: 'CONFIRMED' })).toEqual({
      ok: true,
      url: '/store/shifts/s1',
    });
  });
  it('delete は /store/shifts/:id を DELETE する', async () => {
    await expect(shiftApi.delete('s1')).resolves.toBeUndefined();
  });
  it('mySchedule は /platform/me/schedule を GET する', async () => {
    expect(await shiftApi.mySchedule({ from: '2026-07-19', to: '2026-07-25' })).toEqual({
      ok: true,
      url: '/platform/me/schedule',
    });
  });
  it('submitShiftRequest は /platform/me/shift-requests を POST する', async () => {
    expect(
      await shiftApi.submitShiftRequest({
        store_id: 1,
        work_date: '2026-07-24',
        start_time: '18:00:00',
        end_time: '23:00:00',
      })
    ).toEqual({ ok: true, url: '/platform/me/shift-requests' });
  });
  it('myShiftRequests は /platform/me/shift-requests を GET する', async () => {
    expect(await shiftApi.myShiftRequests()).toEqual({
      ok: true,
      url: '/platform/me/shift-requests',
    });
  });
  it('myStores は /platform/me/stores を GET する', async () => {
    expect(await shiftApi.myStores()).toEqual({ ok: true, url: '/platform/me/stores' });
  });
  it('listShiftRequests は /store/shift-requests を GET する', async () => {
    expect(await shiftApi.listShiftRequests({ status: 'PENDING' })).toEqual({
      ok: true,
      url: '/store/shift-requests',
    });
  });
  it('approveShiftRequest は /store/shift-requests/:id/approval を POST する', async () => {
    expect(await shiftApi.approveShiftRequest('sr1')).toEqual({
      ok: true,
      url: '/store/shift-requests/sr1/approval',
    });
  });
  it('declineShiftRequest は /store/shift-requests/:id/decline を POST する', async () => {
    expect(await shiftApi.declineShiftRequest('sr1')).toEqual({
      ok: true,
      url: '/store/shift-requests/sr1/decline',
    });
  });
});
