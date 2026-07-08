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
  it('list は /tenant/shifts を GET する', async () => {
    expect(await shiftApi.list({ from: '2026-07-01', to: '2026-07-31' })).toEqual({
      ok: true,
      url: '/tenant/shifts',
    });
  });
  it('create は /tenant/shifts を POST する', async () => {
    expect(
      await shiftApi.create({
        cast_id: 'c1',
        work_date: '2026-07-08',
        start_time: '18:00:00',
        end_time: '23:00:00',
      })
    ).toEqual({ ok: true, url: '/tenant/shifts' });
  });
  it('update は /tenant/shifts/:id を PUT する', async () => {
    expect(await shiftApi.update('s1', { status: 'CONFIRMED' })).toEqual({
      ok: true,
      url: '/tenant/shifts/s1',
    });
  });
  it('delete は /tenant/shifts/:id を DELETE する', async () => {
    await expect(shiftApi.delete('s1')).resolves.toBeUndefined();
  });
});
