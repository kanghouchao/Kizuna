import { orderApi } from '@/entities/order';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('orderApi', () => {
  it('list は /store/orders を GET する', async () => {
    expect(await orderApi.list()).toEqual({ ok: true, url: '/store/orders' });
  });
  it('create は /store/orders を POST する', async () => {
    expect(await orderApi.create({} as never)).toEqual({ ok: true, url: '/store/orders' });
  });
});
