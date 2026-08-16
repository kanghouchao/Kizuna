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
    // 一覧は他一覧と同形の Spring Page 形（0 起点）
    mockGet.mockResolvedValueOnce({
      data: { content: [{ id: '1' }], total_pages: 4, total_elements: 35, size: 10, number: 1 },
    });
    const list = await platformStoreApi.getList({ page: 1, size: 10 });
    expect(list).toEqual({ rows: [{ id: '1' }], page: 1, pageCount: 4, total: 35 });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores', {
      params: { page: 1, size: 10, search: undefined },
    });

    mockGet.mockResolvedValueOnce({ data: { id: '2' } });
    const byId = await platformStoreApi.getById('2');
    expect(byId).toEqual({ id: '2' });
    expect(mockGet).toHaveBeenCalledWith('/platform/stores/2');

    // 作成の端点は 201 Created。body には作成された店舗の id だけが載る。
    mockPost.mockResolvedValueOnce({ data: { id: 1 } });
    const created = await platformStoreApi.create({ name: 't' } as any);
    expect(created).toEqual({ id: 1 });
    expect(mockPost).toHaveBeenCalledWith('/platform/stores', { name: 't' });

    // 更新の端点は 204 No Content。body は返らない。
    mockPut.mockResolvedValueOnce({});
    const updated = await platformStoreApi.update('3', { name: 'u' } as any);
    expect(updated).toBeUndefined();
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
