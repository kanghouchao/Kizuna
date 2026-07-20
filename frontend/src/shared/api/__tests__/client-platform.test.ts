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

  it('injects X-Role/X-Store-ID for /store requests when a store console is selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Role']).toBe('store');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it('injects X-Role/X-Store-ID for /files requests when a store console is selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/files/upload');
    expect(headers['X-Role']).toBe('store');
    expect(headers['X-Store-ID']).toBe('2');
  });

  it('does not inject store headers for /platform requests even with a store role selected', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'store';
      if (key === 'platform-store-id') return '2';
      return undefined;
    });
    const headers = await requestTo('/platform/configs');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('skips the legacy x-mw branch entirely when a platform session is active (platform console, no store)', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'platform-role') return 'platform';
      if (key === 'x-mw-role') return 'store';
      if (key === 'x-mw-store-id') return '99';
      return undefined;
    });
    const headers = await requestTo('/store/me');
    expect(headers['X-Role']).toBeUndefined();
    expect(headers['X-Store-ID']).toBeUndefined();
  });

  it('falls back to the legacy x-mw branch when no platform session is active', async () => {
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
