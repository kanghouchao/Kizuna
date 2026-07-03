import { storeProfileApi } from '@/entities/store-profile';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('storeProfileApi', () => {
  it('get は /tenant/config を GET する', async () => {
    expect(await storeProfileApi.get()).toEqual({ ok: true, url: '/tenant/config' });
  });
  it('update は /tenant/config を PUT する', async () => {
    expect(await storeProfileApi.update({})).toEqual({ ok: true, url: '/tenant/config' });
  });
});
