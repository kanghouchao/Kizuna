import { centralTenantApi } from '@/entities/tenant';

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

  it('centralTenantApi basic CRUD and stats', async () => {
    mockGet.mockResolvedValueOnce({ data: { items: [] } });
    const list = await centralTenantApi.getList({ page: 1 });
    expect(list).toEqual({ items: [] });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores', {
      params: { page: 1 },
    });

    mockGet.mockResolvedValueOnce({ data: { id: '2' } });
    const byId = await centralTenantApi.getById('2');
    expect(byId).toEqual({ id: '2' });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores/2');

    mockPost.mockResolvedValueOnce({ data: { id: '3' } });
    const created = await centralTenantApi.create({ name: 't' } as any);
    expect(created).toEqual({ id: '3' });
    expect(mockPost).toHaveBeenCalledWith('/platform/stores', { name: 't' });

    mockPut.mockResolvedValueOnce({ data: { id: '3', name: 'u' } });
    const updated = await centralTenantApi.update('3', { name: 'u' } as any);
    expect(updated).toEqual({ id: '3', name: 'u' });
    expect(mockPut).toHaveBeenCalledWith('/platform/stores/3', { name: 'u' });

    mockDelete.mockResolvedValueOnce({});
    await centralTenantApi.delete('3');
    expect(mockDelete).toHaveBeenCalledWith('/platform/stores/3');

    mockGet.mockResolvedValueOnce({ data: { total: 1 } });
    const stats = await centralTenantApi.getStats();
    expect(stats).toEqual({ total: 1 });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores/stats');
  });
});
