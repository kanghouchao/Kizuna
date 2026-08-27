import { platformStaffAccountApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('platformStaffAccountApi', () => {
  // 一覧は他一覧と同形の Spring Page 形（0 起点）
  it('list は /platform/staff-accounts を page/size/search 付きで GET し正規化ページを返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [{ id: 1 }], total_pages: 3, total_elements: 21, size: 10, number: 1 },
    });

    const res = await platformStaffAccountApi.list({ page: 1, size: 10, search: '山田' });

    expect(res).toEqual({ rows: [{ id: 1 }], page: 1, pageCount: 3, total: 21 });
    expect(apiClient.get).toHaveBeenCalledWith('/platform/staff-accounts', {
      params: { page: 1, size: 10, search: '山田' },
    });
  });

  // 停止・再開は本体を持たない専用端点（版の往復も無い）
  it('suspend は /platform/staff-accounts/{id}/suspension を POST する', async () => {
    await platformStaffAccountApi.suspend(7);

    expect(apiClient.post).toHaveBeenCalledWith('/platform/staff-accounts/7/suspension');
  });

  it('resume は /platform/staff-accounts/{id}/resumption を POST する', async () => {
    await platformStaffAccountApi.resume(7);

    expect(apiClient.post).toHaveBeenCalledWith('/platform/staff-accounts/7/resumption');
  });

  // 仮パスワードの生値はこの応答にしか現れないので、本体をそのまま返す
  it('resetPassword は /platform/staff-accounts/{id}/password-reset を POST し応答本体を返す', async () => {
    (apiClient.post as jest.Mock).mockResolvedValueOnce({
      data: { temporary_password: 'test-temporary-password' },
    });

    const res = await platformStaffAccountApi.resetPassword(7);

    expect(res).toEqual({ temporary_password: 'test-temporary-password' });
    expect(apiClient.post).toHaveBeenCalledWith('/platform/staff-accounts/7/password-reset');
  });
});
