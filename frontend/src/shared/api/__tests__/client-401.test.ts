// mock navigation helper before importing client so that interceptor uses the mock
const redirectMock = jest.fn();
const clearSessionMock = jest.fn();
const rememberMemberReturnPathMock = jest.fn();
let sessionKindValue: string | undefined;
jest.mock('@/shared/lib', () => ({
  // 引数をそのまま渡す。握り潰すと理由コードが未配線でもテストが緑のままになる。
  redirectToLogin: (reason?: string) => redirectMock(reason),
  clearSession: () => clearSessionMock(),
  rememberMemberReturnPath: (value: string, fragment?: string) =>
    rememberMemberReturnPathMock(value, fragment),
  getSessionKind: () => sessionKindValue,
  getCurrentStoreId: () => undefined,
  startSession: jest.fn(),
}));

import { apiClient, getSessionUserId, storeSessionToken } from '@/shared/api';

describe('apiClient 401/403 interceptor', () => {
  const originalHref = window.location.href;
  beforeEach(() => {
    jest.clearAllMocks();
    sessionKindValue = undefined;
    storeSessionToken({ token: 'tkn', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
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

  const sentUrls: string[] = [];
  const withRejectingAdapter = async (status: number, request: () => Promise<unknown>) => {
    const original = apiClient.defaults.adapter;
    sentUrls.length = 0;
    apiClient.defaults.adapter = (async (config: { url?: string }) => {
      sentUrls.push(config.url ?? '');
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

  it('skipAuthRedirect の要求の 401 はリフレッシュもトークンの破棄も差し戻しもしない（呼び出し元が失敗を自前で扱う）', async () => {
    await withRejectingAdapter(401, () =>
      apiClient.post('/auth/login', {}, { skipAuthRedirect: true } as never)
    );

    expect(sentUrls).toEqual(['/auth/login']);
    expect(getSessionUserId()).toBe(7);
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('clears session and redirects on 403 when the session-kind cookie holds a legacy role value (旧トークンのデッドロック回避)', async () => {
    sessionKindValue = 'MANAGER';
    await withRejectingAdapter(403, () => apiClient.get('/orders'));

    expect(getSessionUserId()).toBeUndefined();
    expect(clearSessionMock).toHaveBeenCalled();
    // 401 と同じ理由コード。こちらのトークンは期限内で、古いのは cookie の形式である。
    expect(redirectMock).toHaveBeenCalledWith('expired');
  });

  it.each(['platform', 'store'])(
    'clears session on 403 when the session-kind cookie holds the retired console value %s',
    async value => {
      sessionKindValue = value;
      await withRejectingAdapter(403, () => apiClient.get('/orders'));

      expect(getSessionUserId()).toBeUndefined();
      expect(clearSessionMock).toHaveBeenCalled();
    }
  );

  it('does nothing on 403 when the session-kind cookie holds the admin session value (正当な権限不足はセッションを壊さない)', async () => {
    sessionKindValue = 'admin';
    await withRejectingAdapter(403, () => apiClient.get('/stores'));

    expect(getSessionUserId()).toBe(7);
    expect(clearSessionMock).not.toHaveBeenCalled();
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('does nothing on 403 when the session-kind cookie holds the member session value (会員の正当な 403 はセッションを壊さない)', async () => {
    sessionKindValue = 'member';
    await withRejectingAdapter(403, () => apiClient.get('/staff'));

    expect(getSessionUserId()).toBe(7);
    expect(clearSessionMock).not.toHaveBeenCalled();
    expect(redirectMock).not.toHaveBeenCalled();
  });
});
