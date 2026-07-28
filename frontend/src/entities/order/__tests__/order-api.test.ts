import { orderApi } from '@/entities/order';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;

describe('orderApi', () => {
  it('list は /store/orders を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'o1' }],
        total_pages: 3,
        total_elements: 25,
        size: 10,
        number: 1,
      },
    });

    await expect(orderApi.list({ page: 1, size: 10 })).resolves.toEqual({
      rows: [{ id: 'o1' }],
      page: 1,
      pageCount: 3,
      total: 25,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/orders', { params: { page: 1, size: 10 } });
  });
  it('create は /store/orders を POST する', async () => {
    expect(await orderApi.create({} as never)).toEqual({ ok: true, url: '/store/orders' });
  });
  it('listReceptionists は /store/orders/receptionists を GET する', async () => {
    expect(await orderApi.listReceptionists()).toEqual({
      ok: true,
      url: '/store/orders/receptionists',
    });
  });
});
