import Cookies from 'js-cookie';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { apiClient, clearSessionToken, storeSessionToken } from '@/shared/api';

jest.mock('js-cookie');

describe('apiClient request interceptor', () => {
  const original = apiClient.defaults.adapter;

  beforeEach(() => {
    jest.clearAllMocks();
    clearSessionToken();
    apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => ({
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) as AxiosAdapter;
  });

  afterEach(() => {
    apiClient.defaults.adapter = original;
  });

  it('メモリのアクセストークンを Bearer で付け、cookie からは読まない', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('from-cookie');
    storeSessionToken({ token: 'abc123', expires_at: 1, user_id: 7, refresh_expires_at: 2 });

    const res = await apiClient.get('/me');

    expect(res.config.headers.Authorization).toBe('Bearer abc123');
    expect(res.config.headers.get('X-XSRF-TOKEN')).toBeUndefined();
  });

  it('トークンを持たないときは Authorization を付けない', async () => {
    (Cookies.get as jest.Mock).mockReturnValue('from-cookie');

    const res = await apiClient.get('/casts/public');

    expect(res.config.headers.Authorization).toBeUndefined();
  });
});
