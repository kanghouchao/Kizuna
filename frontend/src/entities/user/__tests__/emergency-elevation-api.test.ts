import { emergencyElevationApi } from '@/entities/user';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('emergencyElevationApi', () => {
  // 再認証失敗の 401 をグローバルなセッション破棄に乗せない（打ち間違いで有効なセッションが消える）
  it('activate は /platform/emergency-elevations へ skipAuthRedirect 付きで POST し応答本体を返す', async () => {
    (apiClient.post as jest.Mock).mockResolvedValueOnce({
      data: { id: 9, token: 'elevated-jwt', expires_at: 123 },
    });

    const res = await emergencyElevationApi.activate({
      store_id: 3,
      reason: '締め処理の代行',
      password: 'secret',
    });

    expect(res).toEqual({ id: 9, token: 'elevated-jwt', expires_at: 123 });
    expect(apiClient.post).toHaveBeenCalledWith(
      '/platform/emergency-elevations',
      { store_id: 3, reason: '締め処理の代行', password: 'secret' },
      { skipAuthRedirect: true }
    );
  });

  it('list は /platform/emergency-elevations を cursor 付きで GET し正規化ページを返す', async () => {
    (apiClient.get as jest.Mock).mockResolvedValueOnce({
      data: { content: [{ id: 1 }], next_cursor: 'abc' },
    });

    const res = await emergencyElevationApi.list({ cursor: 'prev' });

    expect(res).toEqual({ rows: [{ id: 1 }], nextCursor: 'abc' });
    expect(apiClient.get).toHaveBeenCalledWith('/platform/emergency-elevations', {
      params: { cursor: 'prev' },
    });
  });

  it('revoke は /platform/emergency-elevations/{id}/revocation を POST する', async () => {
    await emergencyElevationApi.revoke(7);

    expect(apiClient.post).toHaveBeenCalledWith('/platform/emergency-elevations/7/revocation');
  });
});
