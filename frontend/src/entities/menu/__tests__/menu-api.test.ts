import { menuApi } from '@/entities/menu';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (_url: string) => ({ data: { ok: true, url: _url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async () => ({ data: undefined })),
  },
}));

describe('menu api', () => {
  it('menuApi.getMenus は /platform/menus/me を GET する', async () => {
    expect(await menuApi.getMenus()).toEqual({ ok: true, url: '/platform/menus/me' });
  });
});
