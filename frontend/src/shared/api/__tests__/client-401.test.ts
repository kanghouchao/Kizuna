import Cookies from 'js-cookie';

// mock navigation helper before importing client so that interceptor uses the mock
const redirectMock = jest.fn();
const clearPlatformSessionMock = jest.fn();
let platformConsoleValue: string | undefined;
jest.mock('@/shared/lib', () => ({
  redirectToLogin: () => redirectMock(),
  clearPlatformSession: () => clearPlatformSessionMock(),
  getPlatformConsole: () => platformConsoleValue,
  getPlatformStoreId: () => undefined,
  isStoreConsole: (v: string | undefined) => v === 'store',
}));

import { apiClient } from '@/shared/api';

jest.mock('js-cookie');

describe('apiClient 401/403 interceptor', () => {
  const originalHref = window.location.href;
  beforeEach(() => {
    jest.clearAllMocks();
    platformConsoleValue = undefined;
    (Cookies.get as jest.Mock).mockImplementation((key: string) => {
      if (key === 'token') return 'tkn';
      return undefined;
    });
  });
  afterEach(() => {
    // restore any spies/mocks on location.assign
    jest.restoreAllMocks();
    // restore original URL to avoid affecting other tests
    try {
      window.history.pushState({}, '', originalHref);
    } catch {
      // ignore if pushState fails
    }
  });

  const withRejectingAdapter = async (status: number, request: () => Promise<unknown>) => {
    const original = apiClient.defaults.adapter;
    apiClient.defaults.adapter = (async (config: unknown) => {
      const error: Error & { response?: { status: number }; config?: unknown } = new Error(
        `HTTP ${status}`
      );
      error.response = { status };
      error.config = config;
      return Promise.reject(error);
    }) as typeof apiClient.defaults.adapter;
    await expect(request()).rejects.toBeDefined();
    apiClient.defaults.adapter = original;
  };

  it('clears token and redirects on 401', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(401, () => apiClient.get('/central/me'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    // assert that our navigation helper was called (no real navigation in jsdom)
    expect(redirectMock).toHaveBeenCalled();
  });

  it('does not clear token or redirect on 401 when the request config sets skipAuthRedirect (#327 codex指摘: 招待受諾のインラインログインはグローバル401処理をバイパスする必要がある)', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(401, () =>
      apiClient.post('/platform/login', {}, { skipAuthRedirect: true } as never)
    );

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('clears session and redirects on 403 when the platform-role cookie holds a legacy role value (#398: 旧トークンのデッドロック回避)', async () => {
    platformConsoleValue = 'STORE_MANAGER';
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(403, () => apiClient.get('/tenant/orders'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(clearPlatformSessionMock).toHaveBeenCalled();
    expect(redirectMock).toHaveBeenCalled();
  });

  it('does nothing on 403 when the platform-role cookie holds a console value (正当な権限不足はセッションを壊さない)', async () => {
    platformConsoleValue = 'store';
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(403, () => apiClient.get('/central/tenants'));

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(clearPlatformSessionMock).not.toHaveBeenCalled();
    expect(redirectMock).not.toHaveBeenCalled();
  });
});
