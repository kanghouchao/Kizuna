import Cookies from 'js-cookie';
import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

async function requestTo(url: string) {
  const original = apiClient.defaults.adapter as any;
  apiClient.defaults.adapter = (async (config: any) => ({
    data: {},
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) as any;
  const res = await apiClient.get(url);
  apiClient.defaults.adapter = original;
  return res.config.headers as any;
}

describe('apiClient platform branch', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      return undefined;
    });
    (Cookies.set as jest.Mock).mockImplementation(() => undefined);
    (Cookies.remove as jest.Mock).mockImplementation(() => undefined);
  });

  it('injects X-Role/X-Tenant-ID for /tenant requests when a store console is selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/tenant/me');
    expect(headers['X-Role']).toBe('tenant');
    expect(headers['X-Tenant-ID']).toBe('2');
  });

  it('injects X-Role/X-Tenant-ID for /files requests when a store console is selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/files/upload');
    expect(headers['X-Role']).toBe('tenant');
    expect(headers['X-Tenant-ID']).toBe('2');
  });

  it('does not inject tenant headers for /central requests even with a store role selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/central/menus/me');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Tenant-ID']).toBeUndefined();
  });

  it('skips the legacy x-mw branch entirely when a platform session is active (central console, no store)', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'central';
      if (key === 'x-mw-role') return 'tenant';
      if (key === 'x-mw-tenant-id') return '99';
      return undefined;
    });
    const headers = await requestTo('/tenant/me');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Tenant-ID']).toBeUndefined();
  });

  it('falls back to the legacy x-mw branch when no platform session is active', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'x-mw-role') return 'tenant';
      if (key === 'x-mw-tenant-id') return '7';
      return undefined;
    });
    const headers = await requestTo('/tenant/me');
    expect(headers['X-Role']).toBe('tenant');
    expect(headers['X-Tenant-ID']).toBe('7');
  });
});
