import { platformStaffApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('platformStaffApi', () => {
  it('list は /platform/staff を GET する', async () => {
    const res = await platformStaffApi.list();
    expect(res).toEqual({ ok: true, url: '/platform/staff' });
  });
  it('create は /platform/staff を POST する', async () => {
    const res = await platformStaffApi.create({
      email: 'staff@example.com',
      password: 'pass1234',
      display_name: '新規スタッフ',
      bundle_ids: [2],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1],
    });
    expect(res).toEqual({ ok: true, url: '/platform/staff' });
  });
  it('update は /platform/staff/:id を PUT し version を往復する', async () => {
    const res = await platformStaffApi.update(1, {
      bundle_ids: [1, 2],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
      enabled: false,
      version: 7,
    });
    expect(res).toEqual({ ok: true, url: '/platform/staff/1' });
    // 楽観ロックの往復契約: 更新ボディに version が含まれること
    expect(apiClient.put).toHaveBeenCalledWith(
      '/platform/staff/1',
      expect.objectContaining({ version: 7 })
    );
  });
  it('bundles は /platform/capability-bundles を GET する', async () => {
    const res = await platformStaffApi.bundles();
    expect(res).toEqual({ ok: true, url: '/platform/capability-bundles' });
  });
  it('grantHistory は /platform/staff/:id/grant-history を GET する', async () => {
    const res = await platformStaffApi.grantHistory(3);
    expect(res).toEqual({ ok: true, url: '/platform/staff/3/grant-history' });
  });
});
