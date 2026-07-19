import { customerApi } from '@/entities/customer';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async () => ({ data: undefined })),
  },
}));

describe('customerApi', () => {
  it('list は /store/customers を GET する', async () => {
    expect(await customerApi.list()).toEqual({ ok: true, url: '/store/customers' });
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
