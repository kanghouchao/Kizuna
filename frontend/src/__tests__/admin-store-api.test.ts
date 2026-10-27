const mockGet = jest.fn<Promise<{ data: unknown }>, unknown[]>();
const mockPost = jest.fn<Promise<{ data: { id: number } }>, unknown[]>();
const mockPut = jest.fn<Promise<void>, unknown[]>();
const mockDelete = jest.fn<Promise<void>, unknown[]>();

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}));

import { adminStoreApi } from '@/entities/store';
import { ClientDataError } from '@/shared/lib';

describe('admin store api wrappers', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('adminStoreApi basic CRUD and stats', async () => {
    // 一覧は他一覧と同形の Spring Page 形（0 起点）
    mockGet.mockResolvedValueOnce({
      data: { content: [{ id: '1' }], total_pages: 4, total_elements: 35, size: 10, number: 1 },
    });
    const list = await adminStoreApi.getList({ page: 1, size: 10 });
    expect(list).toEqual({ rows: [{ id: '1' }], page: 1, pageCount: 4, total: 35 });
    expect(mockGet).toHaveBeenCalledWith('/stores', {
      params: { page: 1, size: 10, search: undefined },
    });

    mockGet.mockResolvedValueOnce({ data: { id: '2' } });
    const byId = await adminStoreApi.getById('2');
    expect(byId).toEqual({ id: '2' });
    expect(mockGet).toHaveBeenCalledWith('/stores/2');

    // 作成の端点は 201 Created。body には作成された店舗の id だけが載る。
    mockPost.mockResolvedValueOnce({ data: { id: 1 } });
    const created = await adminStoreApi.create({
      name: 't',
      domain: 't.example.com',
    });
    expect(created).toEqual({ id: 1 });
    expect(mockPost).toHaveBeenCalledWith('/stores', {
      name: 't',
      domain: 't.example.com',
    });

    // 更新の端点は 204 No Content。body は返らない。
    mockPut.mockResolvedValueOnce();
    const updated = await adminStoreApi.update('3', { name: 'u' });
    expect(updated).toBeUndefined();
    expect(mockPut).toHaveBeenCalledWith('/stores/3', {
      name: 'u',
    });

    mockDelete.mockResolvedValueOnce();
    await adminStoreApi.delete('3');
    expect(mockDelete).toHaveBeenCalledWith('/stores/3');

    mockGet.mockResolvedValueOnce({ data: { total: 1 } });
    const stats = await adminStoreApi.getStats();
    expect(stats).toEqual({ total: 1 });
    expect(mockGet).toHaveBeenCalledWith('/stores/stats');
  });
});

/**
 * 識別子を欠いた呼び出しは要求そのものを組まない。応答 DTO の項目はすべて可選なので、画面側が
 * `?? ''` で素通しすると単数の操作が一覧の URI へ飛び、届いた先の 404/405 が操作の失敗と
 * 見分けられなくなる。守りはアダプタの内側にあり、画面ごとに書かない。
 */
describe('識別子を欠いた adminStoreApi', () => {
  const calls: [string, () => Promise<unknown>][] = [
    ['getById', () => adminStoreApi.getById(undefined)],
    ['update', () => adminStoreApi.update(undefined, { name: 'n' })],
    ['delete', () => adminStoreApi.delete(undefined)],
  ];

  it.each(calls)('%s は要求を出さず、名乗る失敗を投げる', async (_name, call) => {
    jest.clearAllMocks();

    await expect(call()).rejects.toBeInstanceOf(ClientDataError);
    await expect(call()).rejects.toThrow('店舗の識別子が取得できていません');
    expect(mockGet).not.toHaveBeenCalled();
    expect(mockPost).not.toHaveBeenCalled();
    expect(mockPut).not.toHaveBeenCalled();
    expect(mockDelete).not.toHaveBeenCalled();
  });
});
