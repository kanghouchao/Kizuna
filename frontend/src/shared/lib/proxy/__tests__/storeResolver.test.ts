import { NextRequest } from 'next/server';
import { resolveStore } from '../storeResolver';

// テスト用の NextRequest モックを作成
const createRequest = (hostname: string, cookies: Record<string, string> = {}) => {
  return {
    headers: {
      get: (name: string) => (name === 'host' ? hostname : null),
    },
    nextUrl: {
      hostname,
    },
    cookies: {
      get: (name: string) => (cookies[name] ? { value: cookies[name] } : undefined),
    },
  } as unknown as NextRequest;
};

describe('storeResolver', () => {
  const originalFetch = global.fetch;
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv };
    process.env.APP_DOMAIN = 'kizuna.test';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    process.env = originalEnv;
  });

  it('identifies platform role for admin domains', async () => {
    const req = createRequest('kizuna.test');
    const result = await resolveStore(req);
    expect(result.role).toBe('platform');
    expect(result.storeData).toBeUndefined();
  });

  it('calls validation API for store domains', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 't1',
          name: 'Store 1',
          template_key: 'custom',
          domain: 'store.test',
        }),
    });

    const req = createRequest('store.test');
    const result = await resolveStore(req);

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('domain=store.test'));
    expect(result.role).toBe('store');
    expect(result.storeData?.isValid).toBe(true);
    expect(result.storeData?.storeId).toBe('t1');
    expect(result.storeData?.storeName).toBe('Store 1');
    expect(result.storeData?.templateKey).toBe('custom');
  });

  it('handles invalid store API response gracefully', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      json: () => Promise.resolve(null), // Empty response
    });

    const req = createRequest('unknown.test');
    const result = await resolveStore(req);

    expect(result.role).toBe('store');
    expect(result.storeData?.isValid).toBe(false);
    expect(result.storeData?.templateKey).toBe('default');
  });

  it('handles API errors gracefully', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network error'));

    const req = createRequest('error.test');
    const result = await resolveStore(req);

    expect(result.role).toBe('store');
    expect(result.storeData?.isValid).toBe(false);
  });

  it('resolves store from cookies when domain matches', async () => {
    const cookies = {
      'x-mw-store-id': 'cookie-store-id',
      'x-mw-store-name': 'Cookie Store',
      'x-mw-store-template': 'cookie-template',
      'x-mw-store-domain': 'store.test', // ドメインが一致
    };
    const req = createRequest('store.test', cookies);

    // Cookie のドメインが一致するため fetch は呼び出されないはず
    global.fetch = jest.fn();

    const result = await resolveStore(req);

    expect(global.fetch).not.toHaveBeenCalled();
    expect(result.role).toBe('store');
    expect(result.storeData?.storeId).toBe('cookie-store-id');
    expect(result.storeData?.storeName).toBe('Cookie Store');
    expect(result.storeData?.templateKey).toBe('cookie-template');
  });

  it('ignores cookies when domain does not match', async () => {
    const cookies = {
      'x-mw-store-id': 'cookie-store-id',
      'x-mw-store-name': 'Cookie Store',
      'x-mw-store-template': 'cookie-template',
      'x-mw-store-domain': 'other-store.test', // ドメインが不一致
    };
    const req = createRequest('store.test', cookies);

    // ドメインが一致しないため fetch が呼び出されるはず
    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 'api-store-id',
          name: 'API Store',
          template_key: 'api-template',
        }),
    });

    const result = await resolveStore(req);

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('domain=store.test'));
    expect(result.role).toBe('store');
    expect(result.storeData?.storeId).toBe('api-store-id');
    expect(result.storeData?.storeName).toBe('API Store');
  });

  it('falls back to API when domain cookie is missing', async () => {
    const cookies = {
      'x-mw-store-id': 'cookie-store-id',
      'x-mw-store-name': 'Cookie Store',
      // x-mw-store-domain が存在しない
    };
    const req = createRequest('store.test', cookies);

    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 'api-store-id',
          name: 'API Store',
        }),
    });

    const result = await resolveStore(req);

    // ドメイン Cookie がないため fetch が呼び出されるはず
    expect(global.fetch).toHaveBeenCalled();
    expect(result.storeData?.storeId).toBe('api-store-id');
  });

  it('fetches template_key from store config when platform response lacks it', async () => {
    // 現実のバックエンド応答（StoreVO）は template_key を返さない
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (String(url).includes('config/public')) {
        return Promise.resolve({
          json: () => Promise.resolve({ template_key: 'modern' }),
        });
      }
      return Promise.resolve({
        json: () =>
          Promise.resolve({
            id: 't1',
            name: 'Store 1',
            domain: 'store.test',
            email: 'owner@store.test',
          }),
      });
    });

    const req = createRequest('store.test');
    const result = await resolveStore(req);

    expect(result.storeData?.templateKey).toBe('modern');
    // 追撃 fetch は X-Role: store + X-Store-ID ヘッダで行う
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('config/public'),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Role': 'store', 'X-Store-ID': 't1' }),
      })
    );
  });

  it('falls back to default when store config fetch fails', async () => {
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (String(url).includes('config/public')) {
        return Promise.reject(new Error('config down'));
      }
      return Promise.resolve({
        json: () => Promise.resolve({ id: 't1', name: 'Store 1', domain: 'store.test' }),
      });
    });

    const req = createRequest('store.test');
    const result = await resolveStore(req);

    expect(result.storeData?.isValid).toBe(true);
    expect(result.storeData?.templateKey).toBe('default');
  });

  it('does not short-circuit when template cookie is missing', async () => {
    const cookies = {
      'x-mw-store-id': 'cookie-store-id',
      'x-mw-store-domain': 'store.test', // ドメインは一致するが template cookie が無い
    };
    const req = createRequest('store.test', cookies);

    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (String(url).includes('config/public')) {
        return Promise.resolve({
          json: () => Promise.resolve({ template_key: 'classic' }),
        });
      }
      return Promise.resolve({
        json: () =>
          Promise.resolve({ id: 'api-store-id', name: 'API Store', domain: 'store.test' }),
      });
    });

    const result = await resolveStore(req);

    // 短絡せずバックエンド再解決に落ちる
    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('domain=store.test'));
    expect(result.storeData?.storeId).toBe('api-store-id');
  });
});
