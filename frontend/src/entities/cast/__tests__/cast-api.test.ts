import { castApi } from '@/entities/cast';

jest.mock('@/shared/api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    post: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    put: jest.fn(async (url: string) => ({ data: { ok: true, url } })),
    delete: jest.fn(async (url: string) => ({ data: undefined })),
  },
}));

describe('castApi', () => {
  it('list は /tenant/casts を GET する', async () => {
    expect(await castApi.list()).toEqual({ ok: true, url: '/tenant/casts' });
  });
  it('get は /tenant/casts/:id を GET する', async () => {
    expect(await castApi.get('c1')).toEqual({ ok: true, url: '/tenant/casts/c1' });
  });
  it('create は /tenant/casts を POST する', async () => {
    expect(await castApi.create({ name: 'A' })).toEqual({ ok: true, url: '/tenant/casts' });
  });
  it('update は /tenant/casts/:id を PUT する', async () => {
    expect(await castApi.update('c1', {})).toEqual({ ok: true, url: '/tenant/casts/c1' });
  });
  it('delete は /tenant/casts/:id を DELETE する', async () => {
    await expect(castApi.delete('c1')).resolves.toBeUndefined();
  });
  it('listPublic は /tenant/casts/public を GET する', async () => {
    expect(await castApi.listPublic()).toEqual({ ok: true, url: '/tenant/casts/public' });
  });
});
