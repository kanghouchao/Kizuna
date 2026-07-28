import { customerApi } from '@/entities/customer';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async () => ({ data: undefined })),
  },
}));

const mockedGet = apiClient.get as jest.Mock;

describe('customerApi', () => {
  it('list は /store/customers を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'c1' }],
        total_pages: 2,
        total_elements: 21,
        size: 20,
        number: 0,
      },
    });

    await expect(customerApi.list({ page: 0, size: 20, search: '山田' })).resolves.toEqual({
      rows: [{ id: 'c1' }],
      page: 0,
      pageCount: 2,
      total: 21,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/customers', {
      params: { page: 0, size: 20, search: '山田' },
    });
  });
  it('get は /store/customers/:id を GET する', async () => {
    expect(await customerApi.get('c1')).toEqual({ ok: true, url: '/store/customers/c1' });
  });
  it('create は /store/customers を POST する', async () => {
    expect(await customerApi.create({ name: 'A' })).toEqual({
      ok: true,
      url: '/store/customers',
    });
  });
  it('update は /store/customers/:id を PUT する', async () => {
    expect(await customerApi.update('c1', {})).toEqual({ ok: true, url: '/store/customers/c1' });
  });
  it('delete は /store/customers/:id を DELETE する', async () => {
    await expect(customerApi.delete('c1')).resolves.toBeUndefined();
  });
});
