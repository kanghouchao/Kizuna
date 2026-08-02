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
    // 失効・停止で終わるセッションは logout を経ないため、me キャッシュもここで破棄される
    window.localStorage.setItem('platform-me-cache', JSON.stringify({ fingerprint: 'x' }));

    await withRejectingAdapter(401, () => apiClient.get('/platform/me'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
    // assert that our navigation helper was called (no real navigation in jsdom)
    expect(redirectMock).toHaveBeenCalled();
  });

  // me キャッシュの要求は expectedToken で束縛される。既に別セッションへ移行済みなら、
  // 陳腐な要求の 401 で新しいセッションを壊してはならない
  it('does not tear down the session on 401 when expectedToken no longer matches the cookie', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(401, () =>
      apiClient.get('/platform/me', { expectedToken: 'old-token' } as never)
    );

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  // cookie が既に無い（失効・除去済み）のは「別セッションへ移行した証拠」ではないため、
  // expectedToken 付きの要求でも通常どおり後始末する（放置すると失効利用者のキャッシュが残り、
  // 画面は読み込み失敗のまま座礁する）
  it('tears down on 401 when the cookie is already gone even if expectedToken is set', async () => {
    (Cookies.get as jest.Mock).mockReturnValue(undefined);
    window.localStorage.setItem('platform-me-cache', JSON.stringify({ fingerprint: 'x' }));

    await withRejectingAdapter(401, () =>
      apiClient.get('/platform/me', { expectedToken: 'expired-token' } as never)
    );

    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
    expect(redirectMock).toHaveBeenCalled();
  });

  it('tears down the session on 401 when expectedToken matches the current cookie', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(401, () =>
      apiClient.get('/platform/me', { expectedToken: 'tkn' } as never)
    );

    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(redirectMock).toHaveBeenCalled();
  });

  it('does not clear token or redirect on 401 when the request config sets skipAuthRedirect (招待受諾のインラインログインはグローバル401処理をバイパスする必要がある)', async () => {
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(401, () =>
      apiClient.post('/platform/login', {}, { skipAuthRedirect: true } as never)
    );

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('clears session and redirects on 403 when the platform-role cookie holds a legacy role value (旧トークンのデッドロック回避)', async () => {
    platformConsoleValue = 'STORE_MANAGER';
    const removeSpy = jest.spyOn(Cookies, 'remove');
    window.localStorage.setItem('platform-me-cache', JSON.stringify({ fingerprint: 'x' }));

    await withRejectingAdapter(403, () => apiClient.get('/store/orders'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(window.localStorage.getItem('platform-me-cache')).toBeNull();
    expect(clearPlatformSessionMock).toHaveBeenCalled();
    expect(redirectMock).toHaveBeenCalled();
  });

  it('does nothing on 403 when the platform-role cookie holds a console value (正当な権限不足はセッションを壊さない)', async () => {
    platformConsoleValue = 'store';
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(403, () => apiClient.get('/platform/stores'));

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(clearPlatformSessionMock).not.toHaveBeenCalled();
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('does nothing on 403 when the platform-role cookie holds the member session value (会員の正当な 403 はセッションを壊さない)', async () => {
    platformConsoleValue = 'member';
    const removeSpy = jest.spyOn(Cookies, 'remove');

    await withRejectingAdapter(403, () => apiClient.get('/platform/staff'));

    expect(removeSpy).not.toHaveBeenCalledWith('token');
    expect(clearPlatformSessionMock).not.toHaveBeenCalled();
    expect(redirectMock).not.toHaveBeenCalled();
  });
});
