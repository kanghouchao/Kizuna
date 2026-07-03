import { centralAuthApi } from '@/entities/user';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async () => ({ data: undefined })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('central api', () => {
  it('me calls /central/me', async () => {
    const res = await centralAuthApi.me();
    expect(res).toEqual({ ok: true, url: '/central/me' });
  });
  it('changePassword PUTs /central/password', async () => {
    await expect(
      centralAuthApi.changePassword({ current_password: 'a', new_password: 'b' })
    ).resolves.toBeUndefined();
  });
  it('logout POSTs /central/logout', async () => {
    await expect(centralAuthApi.logout()).resolves.toBeUndefined();
  });
});
