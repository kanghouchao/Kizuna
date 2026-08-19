import { castApi, castFieldDefinitionApi } from '@/entities/cast';
import { apiClient } from '@/shared/api';
import { ClientDataError } from '@/shared/lib';

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

describe('castApi', () => {
  it('list は /store/casts を GET し、Spring Page を PageResult へ正規化する', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        content: [{ id: 'c1' }],
        total_pages: 5,
        total_elements: 100,
        size: 20,
        number: 2,
      },
    });

    await expect(castApi.list({ page: 2, size: 20 })).resolves.toEqual({
      rows: [{ id: 'c1' }],
      page: 2,
      pageCount: 5,
      total: 100,
    });
    expect(mockedGet).toHaveBeenCalledWith('/store/casts', { params: { page: 2, size: 20 } });
  });
  it('get は /store/casts/:id を GET する', async () => {
    expect(await castApi.get('c1')).toEqual({ ok: true, url: '/store/casts/c1' });
  });
  it('create は /store/casts を POST する', async () => {
    expect(await castApi.create({ name: 'A' })).toEqual({ ok: true, url: '/store/casts' });
  });
  it('update は /store/casts/:id を PUT する', async () => {
    expect(await castApi.update('c1', {})).toEqual({ ok: true, url: '/store/casts/c1' });
  });
  it('delete は /store/casts/:id を DELETE する', async () => {
    await expect(castApi.delete('c1')).resolves.toBeUndefined();
  });
  it('listPublic は /store/casts/public を GET する', async () => {
    expect(await castApi.listPublic()).toEqual({ ok: true, url: '/store/casts/public' });
  });
  it('issueInvitation は /store/casts/:id/invitation を POST する', async () => {
    expect(await castApi.issueInvitation('c1')).toEqual({
      ok: true,
      url: '/store/casts/c1/invitation',
    });
  });
});

describe('castFieldDefinitionApi', () => {
  it('list は /store/casts/fields を GET する', async () => {
    expect(await castFieldDefinitionApi.list()).toEqual({
      ok: true,
      url: '/store/casts/fields',
    });
  });
  it('create は /store/casts/fields を POST する', async () => {
    expect(await castFieldDefinitionApi.create({ key: 'blood_type', label: '血液型' })).toEqual({
      ok: true,
      url: '/store/casts/fields',
    });
  });
  it('update は /store/casts/fields/:id を PUT する', async () => {
    expect(await castFieldDefinitionApi.update('f1', {})).toEqual({
      ok: true,
      url: '/store/casts/fields/f1',
    });
  });
  it('delete は /store/casts/fields/:id を DELETE する', async () => {
    await expect(castFieldDefinitionApi.delete('f1')).resolves.toBeUndefined();
  });
});

/**
 * 識別子を欠いた呼び出しは要求そのものを組まない。応答 DTO の項目はすべて可選なので、画面側が
 * `?? ''` で素通しすると単数の操作が一覧の URI へ飛び、届いた先の 404/405 が操作の失敗と
 * 見分けられなくなる。守りはアダプタの内側にあり、画面ごとに書かない。
 */
describe('識別子を欠いた castApi / castFieldDefinitionApi', () => {
  const calls: [string, () => Promise<unknown>, string][] = [
    ['castApi.get', () => castApi.get(undefined), 'キャスト'],
    ['castApi.update', () => castApi.update(undefined, {}), 'キャスト'],
    ['castApi.delete', () => castApi.delete(undefined), 'キャスト'],
    ['castApi.issueInvitation', () => castApi.issueInvitation(undefined), 'キャスト'],
    [
      'castFieldDefinitionApi.update',
      () =>
        castFieldDefinitionApi.update(undefined, { label: 'x', display_order: 1, is_public: true }),
      'フィールド',
    ],
    ['castFieldDefinitionApi.delete', () => castFieldDefinitionApi.delete(undefined), 'フィールド'],
  ];

  it.each(calls)('%s は要求を出さず、名乗る失敗を投げる', async (_name, call, label) => {
    jest.clearAllMocks();

    await expect(call()).rejects.toBeInstanceOf(ClientDataError);
    await expect(call()).rejects.toThrow(`${label}の識別子が取得できていません`);
    expect(apiClient.get).not.toHaveBeenCalled();
    expect(apiClient.post).not.toHaveBeenCalled();
    expect(apiClient.put).not.toHaveBeenCalled();
    expect(apiClient.delete).not.toHaveBeenCalled();
  });
});
