import Cookies from 'js-cookie';
import { apiClient } from '@/shared/api';
import {
  LoginResponse,
  PasswordChangeRequest,
  PlatformLoginRequest,
  PlatformMeResponse,
  PlatformMeUpdateRequest,
  PlatformStore,
} from '../model/types';

// /platform/me の応答（権限・console・storeBridge）はログイン時に鋳造される JWT が根拠で、
// 次回ログインまで変わらない。そこで応答を token をキーに localStorage へキャッシュし、
// 再ログイン・失効による token の変化で自然に無効化する。表示名の変更（updateMe）は
// 応答でキャッシュを上書きする。権限の強制はサーバ側にあり、これは表示用の複製にすぎない。
const ME_CACHE_KEY = 'platform-me-cache';

function readCachedMe(token: string): PlatformMeResponse | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(ME_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { token?: string; me?: PlatformMeResponse };
    return parsed.token === token && parsed.me ? parsed.me : null;
  } catch {
    // 壊れた保存値は無いものとして扱う（次の取得成功時に上書きされる）
    return null;
  }
}

function writeCachedMe(token: string, me: PlatformMeResponse): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(ME_CACHE_KEY, JSON.stringify({ token, me }));
  } catch {
    // 保存できなくても機能は損なわれない（次回も取得するだけ）
  }
}

/** ログアウト時に呼ぶ。放置しても token 不一致で読まれないが、他者の目に残さない。 */
export function clearMeCache(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(ME_CACHE_KEY);
  } catch {
    // storage が塞がれていても、後続のログアウト処理（cookie 破棄・遷移）を止めない
  }
}

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
    const cached = readCachedMe(token);
    if (cached) return cached;
    if (inflightMe?.token === token) return inflightMe.request;
    const request = apiClient.get('/platform/me').then(response => {
      // 応答待ちの間に logout（キャッシュ破棄 + token 除去）が走った場合に書き戻すと、
      // ログアウト後の共有端末に個人情報が残る。今も同じ token のときだけ書く。
      if (Cookies.get('token') === token) writeCachedMe(token, response.data);
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
    // token は発送前に捕まえ、応答後も同じ token のときだけ書く。応答後に読み直すと、
    // 待機中に別タブで再ログインした場合、新しい token の鍵へ旧利用者の応答を書き込んでしまう。
    const token = Cookies.get('token');
    const response = await apiClient.put('/platform/me', data);
    if (token && Cookies.get('token') === token) writeCachedMe(token, response.data);
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
