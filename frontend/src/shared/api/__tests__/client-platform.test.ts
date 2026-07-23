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

  it('injects X-Role/X-Store-ID for /store requests when the current path carries a storeId', async () => {
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      // 混成束ユーザーは console='platform' のままstore-scopedページを開ける
      if (key === 'platform-role') return 'platform';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Role']).toBe('store');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it('injects X-Role/X-Store-ID for /files requests when the current path carries a storeId', async () => {
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      return undefined;
    });
    const headers = await requestTo('/files/upload');
    expect(headers['X-Role']).toBe('store');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it('does not inject store headers when the current path has no storeId (/store/select)', async () => {
    window.history.pushState({}, '', '/store/select');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('does not inject store headers for /platform requests even when the current path carries a storeId', async () => {
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      return undefined;
    });
    const headers = await requestTo('/platform/configs');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('updates the "last selected store" cookie after a successful response that carried X-Store-ID', async () => {
    // バックエンドが X-Store-ID を受理した成功応答＝StoreIdInterceptor の fail-closed 検証を通過した証拠。
    // このときだけ「前回選択」cookie を更新する（Sidebar の無検証 mount 書き込みを置き換え）。
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      return undefined;
    });

    await requestTo('/store/me');

    expect(Cookies.set).toHaveBeenCalledWith('platform-store-id', '2', undefined);
  });

  it('does not update the "last selected store" cookie when the response carried no X-Store-ID', async () => {
    // /platform 宛や storeId 未確定の応答には X-Store-ID が付かないため cookie は更新しない。
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      return undefined;
    });

    await requestTo('/platform/configs');

    expect(Cookies.set).not.toHaveBeenCalled();
  });

  it('falls back to the legacy x-mw branch when no platform session is active', async () => {
    window.history.pushState({}, '', '/store/2/dashboard');
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'x-mw-role') return 'store';
      if (key === 'x-mw-store-id') return '7';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Role']).toBe('store');
    expect(headers['X-Store-ID']).toBe('7');
  });
});
