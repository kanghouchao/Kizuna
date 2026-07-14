import { platformStaffApi } from '@/entities/user';

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
      role: 'STORE_STAFF',
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1],
    });
    expect(res).toEqual({ ok: true, url: '/platform/staff' });
  });
  it('update は /platform/staff/:id を PUT する', async () => {
    const res = await platformStaffApi.update(1, {
      role: 'STORE_MANAGER',
      store_scope_type: 'ALL_STORES',
      store_ids: [],
    });
    expect(res).toEqual({ ok: true, url: '/platform/staff/1' });
  });
});
