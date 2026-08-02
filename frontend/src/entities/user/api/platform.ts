import Cookies from 'js-cookie';
import {
  apiClient,
  clearMeCache,
  markMeCacheStale,
  readCachedMe,
  writeCachedMe,
} from '@/shared/api';
import {
  LoginResponse,
  PasswordChangeRequest,
  PlatformLoginRequest,
  PlatformMeResponse,
  PlatformMeUpdateRequest,
  PlatformStore,
} from '../model/types';

// /platform/me の応答（権限・console・storeBridge）はログイン時に鋳造される JWT が根拠で、
// 次回ログインまで変わらない。そこで応答を token の一方向指紋をキーに localStorage へ
// キャッシュし、再ログイン・失効による token の変化で自然に無効化する。保管庫は
// shared/api/me-cache（apiClient の強制退場でも破棄できる場所）にあり、ここは取得・変異の
// ライフサイクルだけを持つ。権限の強制はサーバ側にあり、これは表示用の複製にすぎない。

// AuthContext（logout）が使う。保管庫の実体は shared/api/me-cache。
export { clearMeCache };

// 同一 token での同時呼び出し（StrictMode の二重 effect・ログイン直後の連続呼び出し）を 1 本の
// リクエストへ束ねる。失敗はキャッシュせず、次の呼び出しが改めて取得する。
let inflightMe: { token: string; request: Promise<PlatformMeResponse> } | null = null;

export const platformAuthApi = {
  login: async (
    credentials: PlatformLoginRequest,
    options?: { skipAuthRedirect?: boolean }
  ): Promise<LoginResponse> => {
    // 招待受諾のインラインログイン等、呼び出し元が独自にセッションを扱う経路は
    // skipAuthRedirect でグローバルな 401 ハンドリング（token 除去/リダイレクト）から除外する
    const response = await apiClient.post('/platform/login', credentials, {
      skipAuthRedirect: options?.skipAuthRedirect,
    } as any);
    return response.data;
  },
  me: async (): Promise<PlatformMeResponse> => {
    const token = Cookies.get('token');
    if (!token) {
      const response = await apiClient.get('/platform/me');
      return response.data;
    }
    const cached = readCachedMe(token) as PlatformMeResponse | null;
    if (cached) return cached;
    if (inflightMe?.token === token) return inflightMe.request;
    const startedAt = Date.now();
    const request = apiClient.get('/platform/me').then(response => {
      // 応答待ちの間に logout（キャッシュ破棄 + token 除去）が走った場合に書き戻すと、
      // ログアウト後の共有端末に個人情報が残る。今も同じ token のときだけ書き、書き込みの
      // 直後にもう一度確認して、書き込みと交錯した logout の破棄を打ち消さない。
      // 別タブの変異（updateMe）との交錯は失効標（別 key）に対する読み取り側の裁定で
      // 弾かれるため、ここでの事前検査は要らない。
      if (Cookies.get('token') === token) {
        writeCachedMe(token, response.data, startedAt);
        if (Cookies.get('token') !== token) clearMeCache();
      }
      return response.data as PlatformMeResponse;
    });
    inflightMe = { token, request };
    try {
      return await request;
    } finally {
      if (inflightMe?.request === request) inflightMe = null;
    }
  },
  updateMe: async (data: PlatformMeUpdateRequest): Promise<PlatformMeResponse> => {
    // 変異の応答をキャッシュへ書き戻すと、同一 token の並行変異（別タブの updateMe・遅延中の
    // GET）との順序をクライアントでは正しく決められない。失効印だけを残し、次の me() に
    // サーバから取り直させる。token は発送前に捕まえ、応答後も同一のときだけ印を書く
    //（待機中に別タブで再ログインした場合、新しい利用者の枠に印を残さない）。
    const token = Cookies.get('token');
    const response = await apiClient.put('/platform/me', data);
    if (token && Cookies.get('token') === token) markMeCacheStale(token);
    return response.data;
  },
  stores: async (): Promise<PlatformStore[]> => {
    const response = await apiClient.get('/platform/stores/me');
    return response.data;
  },
  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/platform/password', data);
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/platform/logout');
  },
};
