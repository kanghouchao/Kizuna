import { NextRequest } from 'next/server';
import { resolveTenant } from '../tenantResolver';

// Mock NextRequest since we depend on it
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

describe('tenantResolver', () => {
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

  it('identifies central role for admin domains', async () => {
    const req = createRequest('kizuna.test');
    const result = await resolveTenant(req);
    expect(result.role).toBe('central');
    expect(result.tenantData).toBeUndefined();
  });

  it('calls validation API for tenant domains', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 't1',
          name: 'Tenant 1',
          template_key: 'custom',
          domain: 'store.test',
        }),
    });

    const req = createRequest('store.test');
    const result = await resolveTenant(req);

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('domain=store.test'));
    expect(result.role).toBe('tenant');
    expect(result.tenantData?.isValid).toBe(true);
    expect(result.tenantData?.tenantId).toBe('t1');
    expect(result.tenantData?.tenantName).toBe('Tenant 1');
    expect(result.tenantData?.templateKey).toBe('custom');
  });

  it('handles invalid tenant API response gracefully', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      json: () => Promise.resolve(null), // Empty response
    });

    const req = createRequest('unknown.test');
    const result = await resolveTenant(req);

    expect(result.role).toBe('tenant');
    expect(result.tenantData?.isValid).toBe(false);
    expect(result.tenantData?.templateKey).toBe('default');
  });

  it('handles API errors gracefully', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network error'));

    const req = createRequest('error.test');
    const result = await resolveTenant(req);

    expect(result.role).toBe('tenant');
    expect(result.tenantData?.isValid).toBe(false);
  });

  it('resolves tenant from cookies when domain matches', async () => {
    const cookies = {
      'x-mw-tenant-id': 'cookie-tenant-id',
      'x-mw-tenant-name': 'Cookie Tenant',
      'x-mw-tenant-template': 'cookie-template',
      'x-mw-tenant-domain': 'store.test', // ドメインが一致
    };
    const req = createRequest('store.test', cookies);

    // Cookie のドメインが一致するため fetch は呼び出されないはず
    global.fetch = jest.fn();

    const result = await resolveTenant(req);

    expect(global.fetch).not.toHaveBeenCalled();
    expect(result.role).toBe('tenant');
    expect(result.tenantData?.tenantId).toBe('cookie-tenant-id');
    expect(result.tenantData?.tenantName).toBe('Cookie Tenant');
    expect(result.tenantData?.templateKey).toBe('cookie-template');
  });

  it('ignores cookies when domain does not match', async () => {
    const cookies = {
      'x-mw-tenant-id': 'cookie-tenant-id',
      'x-mw-tenant-name': 'Cookie Tenant',
      'x-mw-tenant-template': 'cookie-template',
      'x-mw-tenant-domain': 'other-store.test', // ドメインが不一致
    };
    const req = createRequest('store.test', cookies);

    // ドメインが一致しないため fetch が呼び出されるはず
    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 'api-tenant-id',
          name: 'API Tenant',
          template_key: 'api-template',
        }),
    });

    const result = await resolveTenant(req);

    expect(global.fetch).toHaveBeenCalledWith(expect.stringContaining('domain=store.test'));
    expect(result.role).toBe('tenant');
    expect(result.tenantData?.tenantId).toBe('api-tenant-id');
    expect(result.tenantData?.tenantName).toBe('API Tenant');
  });

  it('falls back to API when domain cookie is missing', async () => {
    const cookies = {
      'x-mw-tenant-id': 'cookie-tenant-id',
      'x-mw-tenant-name': 'Cookie Tenant',
      // x-mw-tenant-domain が存在しない
    };
    const req = createRequest('store.test', cookies);

    global.fetch = jest.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          id: 'api-tenant-id',
          name: 'API Tenant',
        }),
    });

    const result = await resolveTenant(req);

    // ドメイン Cookie がないため fetch が呼び出されるはず
    expect(global.fetch).toHaveBeenCalled();
    expect(result.tenantData?.tenantId).toBe('api-tenant-id');
  });
});
