import { storeAuthApi } from '@/entities/user';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string, _data?: any) => ({
      data:
        url === '/tenant/init-admin-user'
          ? {
              tenant_domain: 'tenant.example.com',
              login_url: 'https://tenant.example.com/login',
              tenant_name: 'Tenant Example',
            }
          : url === '/tenant/login'
            ? { token: 'jwt-token', expires_at: 123 }
            : { ok: true, url },
    })),
  },
}));

describe('tenant api', () => {
  it('register returns data from /tenant/init-admin-user', async () => {
    const res = await storeAuthApi.register({} as any);
    expect(res).toEqual({
      tenant_domain: 'tenant.example.com',
      login_url: 'https://tenant.example.com/login',
      tenant_name: 'Tenant Example',
    });
  });
  it('login returns data from /tenant/login', async () => {
    const res = await storeAuthApi.login({} as any);
    expect(res).toEqual({ token: 'jwt-token', expires_at: 123 });
  });
  it('me calls /tenant/me', async () => {
    const res = await storeAuthApi.me();
    expect(res).toEqual({ ok: true, url: '/tenant/me' });
  });
  it('updateMe PUTs /tenant/me', async () => {
    const res = await storeAuthApi.updateMe({ nickname: 'A' });
    expect(res).toEqual({ ok: true, url: '/tenant/me' });
  });
  it('changePassword PUTs /tenant/password', async () => {
    await expect(
      storeAuthApi.changePassword({ current_password: 'a', new_password: 'b' })
    ).resolves.toBeUndefined();
  });
});
