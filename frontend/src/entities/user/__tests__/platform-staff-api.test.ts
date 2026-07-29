import { platformRoleApi, platformStaffApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('platformStaffApi', () => {
  // 一覧は他一覧と同形の Spring Page 形（0 起点）
  it('list は /platform/staff を page/size/search 付きで GET し正規化ページを返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [{ id: 1 }], total_pages: 3, total_elements: 21, size: 10, number: 1 },
    });

    const res = await platformStaffApi.list({ page: 1, size: 10, search: '山田' });

    expect(res).toEqual({ rows: [{ id: 1 }], page: 1, pageCount: 3, total: 21 });
    expect(apiClient.get).toHaveBeenCalledWith('/platform/staff', {
      params: { page: 1, size: 10, search: '山田' },
    });
  });
  it('get は /platform/staff/{id} を GET する', async () => {
    const res = await platformStaffApi.get(7);
    expect(res).toEqual({ ok: true, url: '/platform/staff/7' });
  });
  it('create は /platform/staff を POST する', async () => {
    const res = await platformStaffApi.create({
      email: 'staff@example.com',
      password: 'pass1234',
      display_name: '新規スタッフ',
      role_ids: [2],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1],
    });
    expect(res).toEqual({ ok: true, url: '/platform/staff' });
    // 授権はロール id の配列で送る（応答側の roles: {id,name}[] とは非対称）
    expect(apiClient.post).toHaveBeenCalledWith(
      '/platform/staff',
      expect.objectContaining({ role_ids: [2] })
    );
  });
  it('update は /platform/staff/:id を PUT し version を往復する', async () => {
    const res = await platformStaffApi.update(1, {
      role_ids: [1, 2],
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
});

describe('platformRoleApi', () => {
  it('list は /platform/roles を GET する', async () => {
    const res = await platformRoleApi.list();
    expect(res).toEqual({ ok: true, url: '/platform/roles' });
  });
  it('create は /platform/roles を POST する', async () => {
    const res = await platformRoleApi.create({
      name: '受付担当',
      permissions: ['ORDER_MANAGE'],
    });
    expect(res).toEqual({ ok: true, url: '/platform/roles' });
    // 権限コードは接頭辞なしの素の enum 名で送る（PERM_ は JWT authorities 内部だけの形式）
    expect(apiClient.post).toHaveBeenCalledWith('/platform/roles', {
      name: '受付担当',
      permissions: ['ORDER_MANAGE'],
    });
  });
  it('update は /platform/roles/:id を PUT し version を往復する', async () => {
    const res = await platformRoleApi.update(5, {
      name: '受付担当',
      permissions: ['ORDER_MANAGE', 'CUSTOMER_MANAGE'],
      version: 3,
    });
    expect(res).toEqual({ ok: true, url: '/platform/roles/5' });
    expect(apiClient.put).toHaveBeenCalledWith(
      '/platform/roles/5',
      expect.objectContaining({ version: 3 })
    );
  });
  it('remove は /platform/roles/:id を DELETE する', async () => {
    await platformRoleApi.remove(5);
    expect(apiClient.delete).toHaveBeenCalledWith('/platform/roles/5');
  });
  it('permissions は /platform/permissions を GET する', async () => {
    const res = await platformRoleApi.permissions();
    expect(res).toEqual({ ok: true, url: '/platform/permissions' });
  });
});
