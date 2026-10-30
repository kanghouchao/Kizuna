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

    const res = await apiClient.get('/me');
    expect(res.config.headers?.Authorization).toBe('Bearer abc123');
    expect(res.config.headers.get('X-XSRF-TOKEN')).toBe('abc123');

    apiClient.defaults.adapter = original;
  });
});
