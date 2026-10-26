import { storeProfileApi } from '@/entities/store-profile';
import { apiClient } from '@/shared/api';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (_url: string) => ({ data: { ok: true, url: _url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async () => ({ data: undefined })),
  },
}));

describe('storeProfileApi', () => {
  it('get は /config を GET する', async () => {
    expect(await storeProfileApi.get()).toEqual({ ok: true, url: '/config' });
  });
  it('update は /config を PUT する', async () => {
    expect(await storeProfileApi.update({})).toEqual({ ok: true, url: '/config' });
  });
  it('activate は /config/activation を本文無しで POST する', async () => {
    await expect(storeProfileApi.activate()).resolves.toBeUndefined();
    expect(apiClient.post).toHaveBeenCalledWith('/config/activation');
  });
});
