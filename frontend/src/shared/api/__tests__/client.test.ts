import Cookies from 'js-cookie';
import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('adds Authorization and CSRF header when cookies exist', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('abc123');
    const original = apiClient.defaults.adapter as any;
    apiClient.defaults.adapter = (async (config: any) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as any;

    const res = await apiClient.get('/platform/me');
    expect(res.config.headers?.Authorization).toBe('Bearer abc123');
    expect((res.config.headers as any)['X-XSRF-TOKEN']).toBe('abc123');

    apiClient.defaults.adapter = original;
  });

  // me キャッシュは「鍵にした token」と「応答を得た token」の一致に依存するため、
  // 呼び出し元が明示束縛した Authorization を cookie で上書きしてはならない
  it('does not overwrite an explicitly bound Authorization header', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('cookie-token');
    const original = apiClient.defaults.adapter as any;
    apiClient.defaults.adapter = (async (config: any) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as any;

    const res = await apiClient.get('/platform/me', {
      headers: { Authorization: 'Bearer captured-token' },
    });
    expect(res.config.headers?.Authorization).toBe('Bearer captured-token');

    apiClient.defaults.adapter = original;
  });

  it('propagates store headers when cookies exist', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'x-mw-role') return 'store';
      if (key === 'x-mw-store-id') return '42';
      return undefined;
    });

    const original = apiClient.defaults.adapter as any;
    apiClient.defaults.adapter = (async (config: any) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as any;

    const res = await apiClient.get('/store/me');
    expect((res.config.headers as any)['X-Role']).toBe('store');
    expect((res.config.headers as any)['X-Store-ID']).toBe('42');
    expect((res.config.headers as any)['X-XSRF-TOKEN']).toBeUndefined();

    apiClient.defaults.adapter = original;
  });
});
