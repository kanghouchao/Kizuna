import { centralTenantApi } from '@/entities/tenant';
import { centralAuthApi } from '@/entities/user';

const mockGet = jest.fn();
const mockPost = jest.fn();
const mockPut = jest.fn();
const mockDelete = jest.fn();

jest.mock('@/shared/api/client', () => ({
  get: (...args: any[]) => mockGet(...args),
  post: (...args: any[]) => mockPost(...args),
  put: (...args: any[]) => mockPut(...args),
  delete: (...args: any[]) => mockDelete(...args),
}));

describe('central api wrappers', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('centralAuthApi.login calls post and returns data', async () => {
    mockPost.mockResolvedValueOnce({ data: { token: 't' } });
    const res = await centralAuthApi.login({ username: 'a', password: 'b' } as any);
    expect(res).toEqual({ token: 't' });
    expect(mockPost).toHaveBeenCalledWith('/central/login', {
      username: 'a',
      password: 'b',
    });
  });

  it('centralAuthApi.me calls get and returns data', async () => {
    mockGet.mockResolvedValueOnce({ data: { id: '1' } });
    const res = await centralAuthApi.me();
    expect(res).toEqual({ id: '1' });
    expect(mockGet).toHaveBeenCalledWith('/central/me');
  });

  it('centralAuthApi.logout calls post', async () => {
    mockPost.mockResolvedValueOnce({});
    await centralAuthApi.logout();
    expect(mockPost).toHaveBeenCalledWith('/central/logout');
  });

  it('centralTenantApi basic CRUD and stats', async () => {
    mockGet.mockResolvedValueOnce({ data: { items: [] } });
    const list = await centralTenantApi.getList({ page: 1 });
    expect(list).toEqual({ items: [] });
    expect(mockGet).toHaveBeenCalledWith('/central/tenants', {
      params: { page: 1 },
    });

    mockGet.mockResolvedValueOnce({ data: { id: '2' } });
    const byId = await centralTenantApi.getById('2');
    expect(byId).toEqual({ id: '2' });
    expect(mockGet).toHaveBeenCalledWith('/central/tenant/2');

    mockPost.mockResolvedValueOnce({ data: { id: '3' } });
    const created = await centralTenantApi.create({ name: 't' } as any);
    expect(created).toEqual({ id: '3' });
    expect(mockPost).toHaveBeenCalledWith('/central/tenant', { name: 't' });

    mockPut.mockResolvedValueOnce({ data: { id: '3', name: 'u' } });
    const updated = await centralTenantApi.update('3', { name: 'u' } as any);
    expect(updated).toEqual({ id: '3', name: 'u' });
    expect(mockPut).toHaveBeenCalledWith('/central/tenant/3', { name: 'u' });

    mockDelete.mockResolvedValueOnce({});
    await centralTenantApi.delete('3');
    expect(mockDelete).toHaveBeenCalledWith('/central/tenant/3');

    mockGet.mockResolvedValueOnce({ data: { total: 1 } });
    const stats = await centralTenantApi.getStats();
    expect(stats).toEqual({ total: 1 });
    expect(mockGet).toHaveBeenCalledWith('/central/tenants/stats');
  });
});
