import { platformStoreApi } from '@/entities/store';

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

describe('platform store api wrappers', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('platformStoreApi basic CRUD and stats', async () => {
    // 一覧だけワイヤ形式が Laravel 風封筒（1 起点）。0 起点の呼び出しとの換算は api 側に閉じている
    mockGet.mockResolvedValueOnce({
      data: { data: [{ id: '1' }], current_page: 2, last_page: 4, total: 35 },
    });
    const list = await platformStoreApi.getList({ page: 1, size: 10 });
    expect(list).toEqual({ rows: [{ id: '1' }], page: 1, pageCount: 4, total: 35 });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores', {
      params: { page: 2, per_page: 10, search: undefined },
    });

    mockGet.mockResolvedValueOnce({ data: { id: '2' } });
    const byId = await platformStoreApi.getById('2');
    expect(byId).toEqual({ id: '2' });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores/2');

    mockPost.mockResolvedValueOnce({ data: { id: '3' } });
    const created = await platformStoreApi.create({ name: 't' } as any);
    expect(created).toEqual({ id: '3' });
    expect(mockPost).toHaveBeenCalledWith('/platform/stores', { name: 't' });

    mockPut.mockResolvedValueOnce({ data: { id: '3', name: 'u' } });
    const updated = await platformStoreApi.update('3', { name: 'u' } as any);
    expect(updated).toEqual({ id: '3', name: 'u' });
    expect(mockPut).toHaveBeenCalledWith('/platform/stores/3', { name: 'u' });

    mockDelete.mockResolvedValueOnce({});
    await platformStoreApi.delete('3');
    expect(mockDelete).toHaveBeenCalledWith('/platform/stores/3');

    mockGet.mockResolvedValueOnce({ data: { total: 1 } });
    const stats = await platformStoreApi.getStats();
    expect(stats).toEqual({ total: 1 });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores/stats');
  });
});
