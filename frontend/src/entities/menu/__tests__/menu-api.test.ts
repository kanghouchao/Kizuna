import { centralMenuApi, storeMenuApi } from '@/entities/menu';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('menu api', () => {
  it('centralMenuApi.getMenus は /central/menus/me を GET する', async () => {
    expect(await centralMenuApi.getMenus()).toEqual({ ok: true, url: '/central/menus/me' });
  });
  it('storeMenuApi.getMenus は /tenant/menus/me を GET する', async () => {
    expect(await storeMenuApi.getMenus()).toEqual({ ok: true, url: '/tenant/menus/me' });
  });
});
