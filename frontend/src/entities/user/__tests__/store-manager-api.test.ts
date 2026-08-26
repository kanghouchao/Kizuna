import { storeManagerApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('storeManagerApi', () => {
  it('list は店舗配下の managers を GET し、分頁しない裸の配列をそのまま返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({ data: [{ id: 1 }] });

    const res = await storeManagerApi.list('7');

    expect(res).toEqual([{ id: 1 }]);
    expect(apiClient.get).toHaveBeenCalledWith('/platform/stores/7/managers');
  });

  it('candidates は manager-candidates を page/size/search 付きで GET し正規化ページを返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [{ id: 2 }], total_pages: 3, total_elements: 11, size: 5, number: 1 },
    });

    const res = await storeManagerApi.candidates('7', { page: 1, size: 5, search: '山田' });

    expect(res).toEqual({ rows: [{ id: 2 }], page: 1, pageCount: 3, total: 11 });
    expect(apiClient.get).toHaveBeenCalledWith('/platform/stores/7/manager-candidates', {
      params: { page: 1, size: 5, search: '山田' },
    });
  });

  // 二択の要求本体はそのまま透過させる。前端で片方へ寄せると、混在の 400 をサーバが判定できない。
  it('appoint は既存アカウントの本体をそのまま POST する', async () => {
    await storeManagerApi.appoint('7', { user_id: 3 });

    expect(apiClient.post).toHaveBeenCalledWith('/platform/stores/7/managers', { user_id: 3 });
  });

  it('appoint は新規作成の本体をそのまま POST する', async () => {
    await storeManagerApi.appoint('7', {
      email: 'new@example.com',
      password: 'secret',
      display_name: '初代店長',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/platform/stores/7/managers', {
      email: 'new@example.com',
      password: 'secret',
      display_name: '初代店長',
    });
  });

  it('dismiss は managers/{userId} を DELETE する', async () => {
    await storeManagerApi.dismiss('7', 3);

    expect(apiClient.delete).toHaveBeenCalledWith('/platform/stores/7/managers/3');
  });
});
