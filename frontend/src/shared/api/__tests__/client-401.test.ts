import Cookies from 'js-cookie';

// mock navigation helper before importing client so that interceptor uses the mock
const redirectMock = jest.fn();
const clearPlatformSessionMock = jest.fn();
const rememberMemberReturnPathMock = jest.fn();
let platformConsoleValue: string | undefined;
jest.mock('@/shared/lib', () => ({
  // 引数をそのまま渡す。握り潰すと理由コードが未配線でもテストが緑のままになる。
  redirectToLogin: (reason?: string) => redirectMock(reason),
  clearPlatformSession: () => clearPlatformSessionMock(),
  rememberMemberReturnPath: (value: string, fragment?: string) =>
    rememberMemberReturnPathMock(value, fragment),
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

    await withRejectingAdapter(401, () => apiClient.get('/platform/me'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    // assert that our navigation helper was called (no real navigation in jsdom)
    // 何も言わずに送り返すと、締め出されたのか自分が何かしたのか分からないまま再入力になる。
    expect(redirectMock).toHaveBeenCalledWith('expired');
  });

  it('401 でログインへ差し戻す前に、会員ポータルの現在地を戻り先として覚える', async () => {
    // 失効した MEMBER token は画面の入口を通ってしまうため、差し戻しがここで初めて起きる。
    // 覚えないとログイン後に店舗つきの申請画面へ戻れない。
    window.history.pushState({}, '', '/member/reservations/new?store=store1.kizuna.test');

    await withRejectingAdapter(401, () => apiClient.get('/platform/me/orders'));

    expect(rememberMemberReturnPathMock).toHaveBeenCalledWith(
      '/member/reservations/new?store=store1.kizuna.test',
      ''
    );
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

    await withRejectingAdapter(403, () => apiClient.get('/store/orders'));

    expect(removeSpy).toHaveBeenCalledWith('token');
    expect(clearPlatformSessionMock).toHaveBeenCalled();
    // 401 と同じ理由コード。こちらのトークンは期限内で、古いのは cookie の形式である。
    expect(redirectMock).toHaveBeenCalledWith('expired');
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
