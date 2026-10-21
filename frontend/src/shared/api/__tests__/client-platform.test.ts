import Cookies from 'js-cookie';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

async function requestTo(url: string) {
  const original = apiClient.defaults.adapter;
  apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => ({
    data: {},
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) as AxiosAdapter;
  const res = await apiClient.get(url);
  apiClient.defaults.adapter = original;
  return res.config.headers;
}

describe('apiClient platform branch', () => {
  const originalHref = window.location.href;

  beforeEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      return undefined;
    });
    (Cookies.set as jest.Mock).mockImplementation(() => undefined);
    (Cookies.remove as jest.Mock).mockImplementation(() => undefined);
  });

  afterEach(() => {
    window.history.pushState({}, '', originalHref);
  });

  const platformSession = (storeId?: string, kind = 'admin') =>
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return kind;
      if (key === 'platform-store-id') return storeId;
      return undefined;
    });

  it.each(['/store/me', '/files'])(
    'injects X-Store-ID from the current-store cookie for %s requests from a store page (no X-Role)',
    async url => {
      window.history.pushState({}, '', '/store/orders');
      platformSession('2');
      const headers = await requestTo(url);
      expect(headers['X-Store-ID']).toBe('2');
      expect(headers['X-Role']).toBeUndefined();
    }
  );

  it.each(['/store/me', '/files'])(
    'does not inject X-Store-ID for %s requests from a /platform page (platform assets stay in the shared area)',
    async url => {
      window.history.pushState({}, '', '/platform/settings');
      platformSession('2');
      const headers = await requestTo(url);
      expect(headers['X-Store-ID']).toBeUndefined();
    }
  );

  it.each(['cast', 'member'])(
    'does not inject a stale current-store cookie for a %s session',
    async kind => {
      window.history.pushState({}, '', '/store/orders');
      platformSession('2', kind);
      const headers = await requestTo('/store/me');
      expect(headers['X-Store-ID']).toBeUndefined();
    }
  );

  it('does not inject X-Store-ID for /platform requests', async () => {
    platformSession('2');
    const headers = await requestTo('/platform/configs');
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('does not inject X-Store-ID while no current store is resolved', async () => {
    window.history.pushState({}, '', '/store/orders');
    platformSession(undefined);
    const headers = await requestTo('/store/me');
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('ignores the store id in a legacy URL: the cookie, not the path, is the store context', async () => {
    window.history.pushState({}, '', '/store/9/orders');
    platformSession('2');
    const headers = await requestTo('/store/me');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it('does not write the current-store cookie from a response', async () => {
    window.history.pushState({}, '', '/store/orders');
    platformSession('2');
    await requestTo('/store/me');
    expect(Cookies.set).not.toHaveBeenCalled();
  });

  it('uses the proxy store cookie (x-mw-store-id) when no platform session is active', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'x-mw-role') return 'store';
      if (key === 'x-mw-store-id') return '7';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Store-ID']).toBe('7');
    expect(headers['X-Role']).toBeUndefined();
  });
});
