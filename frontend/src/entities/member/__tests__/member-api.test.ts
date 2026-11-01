import { memberApi } from '@/entities/member';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
  },
}));

describe('member api', () => {
  it('register POSTs /members', async () => {
    const res = await memberApi.register({
      email: 'member@example.com',
      password: 'password1234',
      display_name: '会員花子',
    });
    expect(res).toEqual({ ok: true, url: '/members' });
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

describe('admin member api', () => {
  const client = () => jest.requireMock('@/shared/api/client').default;

  it('list GETs /members with status, search and paging, and normalizes the page', async () => {
    client().get.mockResolvedValueOnce({
      data: { content: [{ id: 1 }], number: 0, total_pages: 1, total_elements: 1 },
    });
    const { adminMemberApi } = await import('@/entities/member');
    const page = await adminMemberApi.list({ page: 0, size: 20, status: 'WITHDRAWN', search: 'x' });
    expect(client().get).toHaveBeenLastCalledWith('/members', {
      params: { page: 0, size: 20, status: 'WITHDRAWN', search: 'x' },
    });
    expect(page).toEqual({ rows: [{ id: 1 }], page: 0, pageCount: 1, total: 1 });
  });

  it('pointEntries GETs the paged sub-resource and withdraw POSTs the reason', async () => {
    client().get.mockResolvedValueOnce({
      data: { content: [], number: 1, total_pages: 2, total_elements: 21 },
    });
    const { adminMemberApi } = await import('@/entities/member');
    const page = await adminMemberApi.pointEntries(7, { page: 1, size: 20 });
    expect(client().get).toHaveBeenLastCalledWith('/members/7/point-entries', {
      params: { page: 1, size: 20 },
    });
    expect(page).toEqual({ rows: [], page: 1, pageCount: 2, total: 21 });
    await adminMemberApi.withdraw(7, { reason: '電話依頼' });
    expect(client().post).toHaveBeenLastCalledWith('/members/7/withdrawal', {
      reason: '電話依頼',
    });
  });
});
