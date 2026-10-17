import Cookies from 'js-cookie';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('adds Authorization and CSRF header when cookies exist', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('abc123');
    const original = apiClient.defaults.adapter;
    apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as AxiosAdapter;

    const res = await apiClient.get('/platform/me');
    expect(res.config.headers?.Authorization).toBe('Bearer abc123');
    expect(res.config.headers.get('X-XSRF-TOKEN')).toBe('abc123');

    apiClient.defaults.adapter = original;
  });

  it('propagates store headers when cookies exist', async () => {
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 't';
      if (key === 'x-mw-role') return 'store';
      if (key === 'x-mw-store-id') return '42';
      return undefined;
    });

    const original = apiClient.defaults.adapter;
    apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as AxiosAdapter;

    const res = await apiClient.get('/store/me');
    expect(res.config.headers.get('X-Role')).toBe('store');
    expect(res.config.headers.get('X-Store-ID')).toBe('42');
    expect(res.config.headers.get('X-XSRF-TOKEN')).toBeUndefined();

    apiClient.defaults.adapter = original;
  });
});
