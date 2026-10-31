import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';

const redirectMock = jest.fn();
const rememberMemberReturnPathMock = jest.fn();
const startSessionMock = jest.fn();
let currentStoreId: string | undefined;
let onStorePage = false;
jest.mock('@/shared/lib', () => ({
  redirectToLogin: (reason?: string) => redirectMock(reason),
  clearSession: jest.fn(),
  rememberMemberReturnPath: (value: string, fragment?: string) =>
    rememberMemberReturnPathMock(value, fragment),
  getSessionKind: () => 'admin',
  getCurrentStoreId: () => currentStoreId,
  isStorePath: () => onStorePage,
  startSession: (kind: string, expiresAt?: number) => startSessionMock(kind, expiresAt),
}));

import {
  apiClient,
  clearSessionToken,
  getSessionAlert,
  getSessionUserId,
  restoreSession,
  setSessionAlert,
  storeSessionToken,
} from '@/shared/api';

type Reply = { status: number; data?: unknown; headers?: Record<string, string>; delayMs?: number };

/** 宛先ごとの応答を順に返す adapter。送られた要求は宛先と Authorization・ヘッダごと記録する。 */
function serve(routes: Record<string, Reply[]>) {
  const sent: InternalAxiosRequestConfig[] = [];
  apiClient.defaults.adapter = (async (config: InternalAxiosRequestConfig) => {
    sent.push(config);
    const reply = routes[config.url ?? '']?.shift() ?? { status: 500 };
    // 同時に走る要求が同じ refresh を待つ形を作るため、応答を少なくとも 1 tick 遅らせる。
    await new Promise(resolve => setTimeout(resolve, reply.delayMs ?? 0));
    const response = {
      data: reply.data ?? {},
      status: reply.status,
      statusText: '',
      headers: reply.headers ?? {},
      config,
    };
    if (reply.status >= 400) {
      const error = Object.assign(new Error(`HTTP ${reply.status}`), { response, config });
      throw error;
    }
    return response;
  }) as AxiosAdapter;
  return {
    sent,
    calls: (url: string) => sent.filter(c => c.url === url),
  };
}

const issued = (token: string, userId = 7) => ({
  status: 200,
  data: { token, expires_at: 1, user_id: userId, refresh_expires_at: 2_000 },
});

describe('apiClient のリフレッシュ経路', () => {
  const originalAdapter = apiClient.defaults.adapter;

  beforeEach(() => {
    jest.clearAllMocks();
    currentStoreId = undefined;
    onStorePage = false;
    clearSessionToken();
    setSessionAlert(null);
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
  });

  it('同時に届いた複数の 401 はリフレッシュを 1 回に束ね、元の要求をそれぞれ新しいトークンで 1 回だけ再送する', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/orders': [{ status: 401 }, { status: 200, data: 'orders' }],
      '/casts': [{ status: 401 }, { status: 200, data: 'casts' }],
      '/auth/refresh': [issued('new')],
    });

    const [orders, casts] = await Promise.all([apiClient.get('/orders'), apiClient.get('/casts')]);

    expect(orders.data).toBe('orders');
    expect(casts.data).toBe('casts');
    expect(server.calls('/auth/refresh')).toHaveLength(1);
    expect(server.calls('/orders').map(c => c.headers.Authorization)).toEqual([
      'Bearer old',
      'Bearer new',
    ]);
    expect(server.calls('/casts')).toHaveLength(2);
    // session-kind の期限はリフレッシュトークンの期限に揃えて延ばす
    expect(startSessionMock).toHaveBeenCalledWith('admin', 2_000);
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('リフレッシュが済んだ後に届いた旧トークンの 401 は、もうリフレッシュせずに新しいトークンで再送する', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/orders': [
        { status: 401, delayMs: 20 },
        { status: 200, data: 'orders' },
      ],
      '/auth/refresh': [issued('new'), issued('newer')],
    });
    // 旧トークンで出た要求の応答が届く前に、別の要求のリフレッシュが終わっている
    const pending = apiClient.get('/orders');
    await restoreSession();

    expect((await pending).data).toBe('orders');
    expect(server.calls('/auth/refresh')).toHaveLength(1);
    expect(server.calls('/orders').map(c => c.headers.Authorization)).toEqual([
      'Bearer old',
      'Bearer new',
    ]);
  });

  it('再送は最初の要求の店舗のまま送り、待つ間に別タブで切り替わった現在店舗を付け直さない', async () => {
    // 店舗を本文に持たない書き込みは X-Store-ID の店舗へ作られる。付け直すと別の店舗へ書き込む。
    onStorePage = true;
    currentStoreId = '1';
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/customers': [{ status: 401 }, { status: 201 }],
      '/auth/refresh': [issued('new')],
    });

    const pending = apiClient.post('/customers', {});
    // 最初の要求が出た後、応答を待つ間に別タブで店舗を切り替える
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(server.calls('/customers')).toHaveLength(1);
    currentStoreId = '2';
    await pending;

    expect(server.calls('/customers').map(c => c.headers['X-Store-ID'])).toEqual(['1', '1']);
  });

  it('再ログインを促している間は、後から届いた 401 でリフレッシュも差し戻しもしない', async () => {
    // credential_changed の応答はリフレッシュ cookie も消すので、取り直すと missing で自動遷移してしまう。
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/orders': [{ status: 401 }],
      '/casts': [{ status: 401 }],
      '/auth/refresh': [
        { status: 401, headers: { 'x-auth-failure': 'credential_changed' } },
        { status: 401, headers: { 'x-auth-failure': 'missing' } },
      ],
    });

    await expect(apiClient.get('/orders')).rejects.toBeDefined();
    await expect(apiClient.get('/casts')).rejects.toBeDefined();

    expect(server.calls('/auth/refresh')).toHaveLength(1);
    expect(getSessionAlert()).toBe('credential_changed');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('再送も 401 なら、もうリフレッシュせずにログインへ差し戻す', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/orders': [{ status: 401 }, { status: 401 }],
      '/auth/refresh': [issued('new'), issued('newer')],
    });

    await expect(apiClient.get('/orders')).rejects.toBeDefined();

    expect(server.calls('/auth/refresh')).toHaveLength(1);
    expect(server.calls('/orders')).toHaveLength(2);
    expect(redirectMock).toHaveBeenCalledWith('expired');
  });

  it('リフレッシュ自身の 401 は再びリフレッシュせず、戻り先を預けてログインへ差し戻す', async () => {
    window.history.pushState({}, '', '/member/receipts#tok3n');
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({
      '/me': [{ status: 401 }],
      '/auth/refresh': [{ status: 401, headers: { 'x-auth-failure': 'expired' } }],
    });

    await expect(apiClient.get('/me')).rejects.toBeDefined();

    expect(server.calls('/auth/refresh')).toHaveLength(1);
    expect(server.calls('/me')).toHaveLength(1);
    expect(rememberMemberReturnPathMock).toHaveBeenCalledWith('/member/receipts', '#tok3n');
    expect(redirectMock).toHaveBeenCalledWith('expired');
    expect(getSessionUserId()).toBeUndefined();
  });

  it('トークンを持たない来訪者のリフレッシュ失敗は、失効を名乗らずにログインへ送る', async () => {
    serve({ '/auth/refresh': [{ status: 401, headers: { 'x-auth-failure': 'missing' } }] });

    expect(await restoreSession()).toEqual({ status: 'unauthenticated' });

    expect(redirectMock).toHaveBeenCalledWith(undefined);
  });

  it('資格情報の変更による 401 は自動では遷移せず、再ログインを促す表示を出す', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    serve({
      '/orders': [{ status: 401 }],
      '/auth/refresh': [{ status: 401, headers: { 'x-auth-failure': 'credential_changed' } }],
    });

    await expect(apiClient.get('/orders')).rejects.toBeDefined();

    expect(getSessionAlert()).toBe('credential_changed');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('別のアカウントのリフレッシュ cookie による 409 は、再読込を促す表示を出して自動では再読込しない', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    serve({
      '/orders': [{ status: 401 }],
      '/auth/refresh': [{ status: 409, data: { error: 'session user mismatch' } }],
    });

    await expect(apiClient.get('/orders')).rejects.toBeDefined();

    expect(getSessionAlert()).toBe('conflict');
    expect(redirectMock).not.toHaveBeenCalled();
    // 画面はまだ旧アカウントのまま動く。手元のトークンは残す。
    expect(getSessionUserId()).toBe(7);
  });

  it('リフレッシュはメモリの利用者 ID を X-Session-User-Id で名乗り、成功すればトークンと利用者 ID を置き換える', async () => {
    storeSessionToken({ token: 'old', expires_at: 1, user_id: 7, refresh_expires_at: 1_000 });
    const server = serve({ '/auth/refresh': [issued('new', 7)] });

    expect(await restoreSession()).toEqual({ status: 'ok', refreshExpiresAt: 2_000 });

    const [refresh] = server.calls('/auth/refresh');
    expect(refresh.headers['X-Session-User-Id']).toBe('7');
    expect(refresh.headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(getSessionUserId()).toBe(7);
  });

  it('タブを開いた直後（利用者 ID が無い）のリフレッシュは名乗らずに送り、応答の利用者を持つ', async () => {
    const server = serve({ '/auth/refresh': [issued('first', 9)] });

    await restoreSession();

    expect(server.calls('/auth/refresh')[0].headers['X-Session-User-Id']).toBeUndefined();
    expect(getSessionUserId()).toBe(9);
  });
});
