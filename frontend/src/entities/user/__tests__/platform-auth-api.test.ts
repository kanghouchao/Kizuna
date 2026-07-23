import { platformAuthApi } from '@/entities/user';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('platform api', () => {
  it('login POSTs /platform/login', async () => {
    const res = await platformAuthApi.login({ email: 'a@example.com', password: 'pass' });
    expect(res).toEqual({ ok: true, url: '/platform/login' });
  });
  it('login は options.skipAuthRedirect を渡すと config に skipAuthRedirect を積む（招待受諾のインラインログインをグローバル401処理から除外する）', async () => {
    const client = jest.requireMock('@/shared/api/client').default;
    await platformAuthApi.login(
      { email: 'a@example.com', password: 'pass' },
      { skipAuthRedirect: true }
    );
    const config = client.post.mock.calls[client.post.mock.calls.length - 1][2];
    expect(config).toMatchObject({ skipAuthRedirect: true });
  });
  it('me calls /platform/me', async () => {
    const res = await platformAuthApi.me();
    expect(res).toEqual({ ok: true, url: '/platform/me' });
  });
  it('updateMe PUTs /platform/me', async () => {
    const res = await platformAuthApi.updateMe({ display_name: 'A' });
    expect(res).toEqual({ ok: true, url: '/platform/me' });
  });
  it('stores calls /platform/stores/me', async () => {
    const res = await platformAuthApi.stores();
    expect(res).toEqual({ ok: true, url: '/platform/stores/me' });
  });
  it('changePassword PUTs /platform/password', async () => {
    await expect(
      platformAuthApi.changePassword({ current_password: 'a', new_password: 'b' })
    ).resolves.toBeUndefined();
  });
  it('logout POSTs /platform/logout', async () => {
    await expect(platformAuthApi.logout()).resolves.toBeUndefined();
  });
});
