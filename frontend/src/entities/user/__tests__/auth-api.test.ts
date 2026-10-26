import { authApi } from '@/entities/user';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('auth api', () => {
  it('login POSTs /auth/login', async () => {
    const res = await authApi.login({ email: 'a@example.com', password: 'pass' });
    expect(res).toEqual({ ok: true, url: '/auth/login' });
  });
  it('login は options.skipAuthRedirect を渡すと config に skipAuthRedirect を積む（招待受諾のインラインログインをグローバル401処理から除外する）', async () => {
    const client = jest.requireMock('@/shared/api/client').default;
    await authApi.login({ email: 'a@example.com', password: 'pass' }, { skipAuthRedirect: true });
    const config = client.post.mock.calls[client.post.mock.calls.length - 1][2];
    expect(config).toMatchObject({ skipAuthRedirect: true });
  });
  it('me calls /me', async () => {
    const res = await authApi.me();
    expect(res).toEqual({ ok: true, url: '/me' });
  });
  it('updateMe PUTs /me', async () => {
    const res = await authApi.updateMe({ display_name: 'A' });
    expect(res).toEqual({ ok: true, url: '/me' });
  });
  it('stores calls /stores/me', async () => {
    const res = await authApi.stores();
    expect(res).toEqual({ ok: true, url: '/stores/me' });
  });
  it('changePassword PUTs /me/password', async () => {
    const client = jest.requireMock('@/shared/api/client').default;
    const body = { current_password: 'a', new_password: 'b' };

    await expect(authApi.changePassword(body)).resolves.toBeUndefined();

    expect(client.put).toHaveBeenLastCalledWith('/me/password', body);
  });
  it('logout POSTs /auth/logout', async () => {
    await expect(authApi.logout()).resolves.toBeUndefined();
  });
  it('logout は skipAuthRedirect を積む（失効済みトークンの 401 で行き先が二重にならない）', async () => {
    // パスワード変更は直前にセッションを畳むため、この退出は 401 になる。グローバルな差し戻しに
    // 乗せると、呼び出し元が決めた行き先とログイン画面への全画面遷移が競合する。
    const client = jest.requireMock('@/shared/api/client').default;
    await authApi.logout();
    const config = client.post.mock.calls[client.post.mock.calls.length - 1][2];
    expect(config).toMatchObject({ skipAuthRedirect: true });
  });
});
