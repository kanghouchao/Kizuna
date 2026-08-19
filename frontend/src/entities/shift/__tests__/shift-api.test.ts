import { shiftApi } from '@/entities/shift';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;

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
  it('changePublication は /store/shifts/:id/publication へ望む状態を明示して PUT する', async () => {
    // 相対的な反転で送ると、逐行の一括操作が取りこぼした行を裏返してしまう
    expect(await shiftApi.changePublication('s1', false)).toEqual({
      ok: true,
      url: '/store/shifts/s1/publication',
    });
    expect(apiClient.put).toHaveBeenCalledWith('/store/shifts/s1/publication', {
      published: false,
    });
  });
  it('delete は /store/shifts/:id を DELETE する', async () => {
    await expect(shiftApi.delete('s1')).resolves.toBeUndefined();
  });
  it('confirmedCasts は /platform/shifts/casts を GET する', async () => {
    expect(await shiftApi.confirmedCasts({ store_id: 1, date: '2026-08-10' })).toEqual({
      ok: true,
      url: '/platform/shifts/casts',
    });
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
  it('submitShiftChangeRequest は /platform/me/shift-requests/changes を POST する', async () => {
    expect(
      await shiftApi.submitShiftChangeRequest({
        shift_id: 'sh1',
        work_date: '2026-07-24',
        start_time: '19:00:00',
        end_time: '22:00:00',
      })
    ).toEqual({ ok: true, url: '/platform/me/shift-requests/changes' });
  });
  it('myShiftRequests は /platform/me/shift-requests を GET し、カーソルページを正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { content: [{ id: 'r1' }], next_cursor: 'abc' },
    });

    await expect(shiftApi.myShiftRequests({ cursor: 'x' })).resolves.toEqual({
      rows: [{ id: 'r1' }],
      nextCursor: 'abc',
    });
    expect(mockedGet).toHaveBeenLastCalledWith('/platform/me/shift-requests', {
      params: { cursor: 'x' },
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
    // 本体を付けないことが「既定の公開可で生まれる」の表明。空オブジェクトを送ると
    // 変更申請の承認で後端の拒否条件に触れる
    expect(apiClient.post).toHaveBeenCalledWith('/store/shift-requests/sr1/approval', undefined);
  });
  it('approveShiftRequest は公開可否を渡すと本体に載せる', async () => {
    await shiftApi.approveShiftRequest('sr1', false);
    expect(apiClient.post).toHaveBeenCalledWith('/store/shift-requests/sr1/approval', {
      published: false,
    });
  });
  it('declineShiftRequest は /store/shift-requests/:id/rejection を POST する', async () => {
    expect(await shiftApi.declineShiftRequest('sr1')).toEqual({
      ok: true,
      url: '/store/shift-requests/sr1/rejection',
    });
  });
});
