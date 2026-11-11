import Cookies from 'js-cookie';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

async function requestTo(url: string, headers?: Record<string, string>) {
  const original = apiClient.defaults.adapter;
  apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => ({
    data: {},
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) as AxiosAdapter;
  const res = await apiClient.get(url, headers ? { headers } : undefined);
  apiClient.defaults.adapter = original;
  return res.config.headers;
}

describe('apiClient session branch', () => {
  const originalHref = window.location.href;

  beforeEach(() => {
    jest.clearAllMocks();
    (Cookies.get as jest.Mock).mockReturnValue(undefined);
    (Cookies.set as jest.Mock).mockImplementation(() => undefined);
    (Cookies.remove as jest.Mock).mockImplementation(() => undefined);
  });

  afterEach(() => {
    window.history.pushState({}, '', originalHref);
  });

  const session = (kind = 'admin') =>
    (Cookies.get as jest.Mock).mockImplementation((key: string) =>
      key === 'session-kind' ? kind : undefined
    );

  it.each(['/orders', '/files', '/me', '/stores/me', '/menus/me'])(
    'injects X-Store-ID from the URL store id for %s requests from a store page (the API path does not decide)',
    async url => {
      window.history.pushState({}, '', '/store/2/orders');
      session();
      const headers = await requestTo(url);
      expect(headers['X-Store-ID']).toBe('2');
    }
  );

  it.each(['/store/2', '/store/2/'])('injects X-Store-ID on the store root %s', async pathname => {
    window.history.pushState({}, '', pathname);
    session();
    const headers = await requestTo('/orders');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it.each(['/orders', '/stores/me', '/files'])(
    'does not inject X-Store-ID for %s requests from a /admin page (group-wide assets stay in the shared area)',
    async url => {
      window.history.pushState({}, '', '/admin/settings');
      session();
      const headers = await requestTo(url);
      expect(headers['X-Store-ID']).toBeUndefined();
    }
  );

  it.each(['/', '/stores', '/storefront/2/orders', '/store', '/store/abc/orders'])(
    'does not inject X-Store-ID from %s, which names no store',
    async pathname => {
      window.history.pushState({}, '', pathname);
      session();
      const headers = await requestTo('/orders');
      expect(headers['X-Store-ID']).toBeUndefined();
    }
  );

  it.each(['cast', 'member'])(
    'injects X-Store-ID from a store page even when the shared session-kind cookie reads %s (another tab rewrote it)',
    async kind => {
      // session-kind は全タブ共有で、別タブのログインが書き換える。このタブの URL（/store 配下は従業員のシェルだけ）で決める。
      window.history.pushState({}, '', '/store/2/orders');
      session(kind);
      const headers = await requestTo('/customers');
      expect(headers['X-Store-ID']).toBe('2');
    }
  );

  it.each(['/cast/schedule', '/member/'])(
    'does not inject X-Store-ID from the portal page %s',
    async pathname => {
      window.history.pushState({}, '', pathname);
      session();
      const headers = await requestTo('/me');
      expect(headers['X-Store-ID']).toBeUndefined();
    }
  );

  it('keeps the store a cross-store list names explicitly from a /admin page', async () => {
    window.history.pushState({}, '', '/admin/orders');
    session();
    const headers = await requestTo('/orders/o1/corrections', { 'X-Store-ID': '5' });
    expect(headers['X-Store-ID']).toBe('5');
  });

  it('keeps an explicitly named store on a store page instead of the URL store', async () => {
    // 結果不明の操作は起こした店舗に固定して送る。
    window.history.pushState({}, '', '/store/2/reviews');
    session();
    const headers = await requestTo('/reviews', { 'X-Store-ID': '5' });
    expect(headers['X-Store-ID']).toBe('5');
  });

  it('reads no current-store cookie and writes no cookie', async () => {
    window.history.pushState({}, '', '/store/2/orders');
    session();
    await requestTo('/stores/me');
    expect(Cookies.get).not.toHaveBeenCalledWith('current-store-id');
    expect(Cookies.set).not.toHaveBeenCalled();
  });
});
