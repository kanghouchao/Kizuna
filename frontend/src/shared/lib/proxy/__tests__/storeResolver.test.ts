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
    delete process.env.BACKEND_URL;
  });

  afterEach(() => {
    global.fetch = originalFetch;
    process.env = originalEnv;
  });

  it('identifies app role for the app domain', async () => {
    const req = createRequest('kizuna.test');
    const result = await resolveStore(req);
    expect(result.role).toBe('app');
    expect(result.storeData).toBeUndefined();
  });

  it('calls validation API for store domains', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
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

    expect(global.fetch).toHaveBeenCalledWith(
      'http://backend:8080/stores/lookup?domain=store.test'
    );
    expect(result.role).toBe('store');
    expect(result.role).toBe('store');
    expect(result.storeData?.storeId).toBe('t1');
    expect(result.storeData?.storeName).toBe('Store 1');
    expect(result.storeData?.templateKey).toBe('custom');
  });

  it('resolves an unregistered host to the unknown role on a lookup 404', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({ status: 404, error: 'Not Found' }),
    });

    const result = await resolveStore(createRequest('unknown.test'));

    expect(result).toEqual({ role: 'unknown' });
  });

  it('does not mistake a backend failure for an unregistered host', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: () => Promise.resolve(null),
    });

    await expect(resolveStore(createRequest('store.test'))).rejects.toThrow('503');
  });

  it('propagates network errors instead of resolving to an empty store', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network error'));

    await expect(resolveStore(createRequest('error.test'))).rejects.toThrow('Network error');
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
      ok: true,
      status: 200,
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
      ok: true,
      status: 200,
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

  it('fetches template_key from store config when the lookup response lacks it', async () => {
    // 現実のバックエンド応答（StoreVO）は template_key を返さない
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (String(url).includes('config/public')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ template_key: 'modern' }),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
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
    // 追加の fetch は X-Store-ID ヘッダだけで行う
    expect(global.fetch).toHaveBeenCalledWith(
      'http://backend:8080/config/public',
      expect.objectContaining({
        headers: { 'X-Store-ID': 't1' },
      })
    );
  });

  it('builds both backend paths on BACKEND_URL, which carries only the host', async () => {
    process.env.BACKEND_URL = 'http://other-backend:9000';
    global.fetch = jest.fn().mockImplementation((url: string) =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve(
            String(url).includes('config/public')
              ? { template_key: 'modern' }
              : { id: 't1', name: 'Store 1', domain: 'store.test' }
          ),
      })
    );

    await resolveStore(createRequest('store.test'));

    expect(global.fetch).toHaveBeenCalledWith(
      'http://other-backend:9000/stores/lookup?domain=store.test'
    );
    expect(global.fetch).toHaveBeenCalledWith(
      'http://other-backend:9000/config/public',
      expect.anything()
    );
  });

  it('falls back to default when store config fetch fails', async () => {
    global.fetch = jest.fn().mockImplementation((url: string) => {
      if (String(url).includes('config/public')) {
        return Promise.reject(new Error('config down'));
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ id: 't1', name: 'Store 1', domain: 'store.test' }),
      });
    });

    const req = createRequest('store.test');
    const result = await resolveStore(req);

    expect(result.role).toBe('store');
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
          ok: true,
          status: 200,
          json: () => Promise.resolve({ template_key: 'classic' }),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
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
