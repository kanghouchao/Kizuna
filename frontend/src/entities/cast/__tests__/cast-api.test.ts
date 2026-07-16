import { castApi, castFieldDefinitionApi } from '@/entities/cast';

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
  it('issueInvitation は /tenant/casts/:id/invitation を POST する', async () => {
    expect(await castApi.issueInvitation('c1')).toEqual({
      ok: true,
      url: '/tenant/casts/c1/invitation',
    });
  });
});

describe('castFieldDefinitionApi', () => {
  it('list は /tenant/casts/fields を GET する', async () => {
    expect(await castFieldDefinitionApi.list()).toEqual({
      ok: true,
      url: '/tenant/casts/fields',
    });
  });
  it('create は /tenant/casts/fields を POST する', async () => {
    expect(await castFieldDefinitionApi.create({ key: 'blood_type', label: '血液型' })).toEqual({
      ok: true,
      url: '/tenant/casts/fields',
    });
  });
  it('update は /tenant/casts/fields/:id を PUT する', async () => {
    expect(await castFieldDefinitionApi.update('f1', {})).toEqual({
      ok: true,
      url: '/tenant/casts/fields/f1',
    });
  });
  it('delete は /tenant/casts/fields/:id を DELETE する', async () => {
    await expect(castFieldDefinitionApi.delete('f1')).resolves.toBeUndefined();
  });
});
