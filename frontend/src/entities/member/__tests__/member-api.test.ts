import { memberApi } from '@/entities/member';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('member api', () => {
  it('register POSTs /admin/members', async () => {
    const res = await memberApi.register({
      email: 'member@example.com',
      password: 'password1234',
      display_name: '会員花子',
    });
    expect(res).toEqual({ ok: true, url: '/admin/members' });
  });

  it('home GETs /me/member', async () => {
    const res = await memberApi.home();
    expect(res).toEqual({ ok: true, url: '/me/member' });
  });

  it('withdraw POSTs /me/member/withdrawal with the reason and returns nothing', async () => {
    const client = jest.requireMock('@/shared/api/client').default;
    await expect(memberApi.withdraw({ reason: '引っ越し' })).resolves.toBeUndefined();
    expect(client.post).toHaveBeenCalledWith('/me/member/withdrawal', { reason: '引っ越し' });
  });
});
