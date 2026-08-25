import { storeStaffApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('storeStaffApi', () => {
  it('list は /store/staff-members を page/size/search 付きで GET し正規化ページを返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [{ id: 1 }], total_pages: 3, total_elements: 21, size: 10, number: 1 },
    });

    const res = await storeStaffApi.list({ page: 1, size: 10, search: '山田' });

    expect(res).toEqual({ rows: [{ id: 1 }], page: 1, pageCount: 3, total: 21 });
    expect(apiClient.get).toHaveBeenCalledWith('/store/staff-members', {
      params: { page: 1, size: 10, search: '山田' },
    });
  });
  // 店舗の絞り込みはクエリではなく店舗文脈ヘッダ（X-Role / X-Store-ID）が担う。
  // 一覧の作用域を URL に持たせないのが、この面と管理者管理の分かれ目である。
  it('list は店舗をクエリに載せない', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [], total_pages: 0, total_elements: 0, size: 10, number: 0 },
    });

    await storeStaffApi.list({ page: 0, size: 10 });

    expect(apiClient.get).toHaveBeenCalledWith('/store/staff-members', {
      params: { page: 0, size: 10, search: undefined },
    });
  });
  it('get は /store/staff-members/{id} を GET する', async () => {
    const res = await storeStaffApi.get(7);
    expect(res).toEqual({ ok: true, url: '/store/staff-members/7' });
  });
  it('create は /store/staff-members を POST する', async () => {
    const res = await storeStaffApi.create({
      email: 'clerk@example.com',
      password: 'pass1234',
      display_name: '新規スタッフ',
      role_ids: [2],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1],
    });
    expect(res).toEqual({ ok: true, url: '/store/staff-members' });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/store/staff-members',
      expect.objectContaining({ role_ids: [2] })
    );
  });
  it('update は /store/staff-members/:id を PUT し version を往復する', async () => {
    const res = await storeStaffApi.update(1, {
      role_ids: [1, 2],
      store_scope_type: 'SPECIFIC_STORES',
      store_ids: [1],
      enabled: false,
      version: 7,
    });
    expect(res).toEqual({ ok: true, url: '/store/staff-members/1' });
    expect(apiClient.put).toHaveBeenCalledWith(
      '/store/staff-members/1',
      expect.objectContaining({ version: 7 })
    );
  });
  // 可授ロールは専用の読み口から取る。/platform/roles は ROLE_MANAGE 門で店長には読めず、
  // 「付与してよいロール」の判定もサーバ側の単源に置いている。
  it('grantableRoles は /store/staff-members/grantable-roles を GET する', async () => {
    const res = await storeStaffApi.grantableRoles();
    expect(res).toEqual({ ok: true, url: '/store/staff-members/grantable-roles' });
  });
});
