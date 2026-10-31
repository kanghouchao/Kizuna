import axios from 'axios';
import type { AxiosError, AxiosHeaders } from 'axios';
import {
  clearSession,
  getSessionKind,
  getCurrentStoreId,
  isStorePath,
  redirectToLogin,
  rememberMemberReturnPath,
  startSession,
} from '@/shared/lib';
import {
  clearSessionToken,
  getAccessToken,
  getSessionAlert,
  getSessionUserId,
  setSessionAlert,
  storeSessionToken,
} from './session';
import type { TokenResponse } from './types';

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean;
    /** リフレッシュ後の再送であること。再送の 401 はもうリフレッシュしない。 */
    authRetried?: boolean;
  }
}

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
});

apiClient.interceptors.request.use(
  config => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // 店舗文脈は X-Store-ID だけで名乗る。従業員の店舗級画面（/store 配下）からの要求には宛先を問わず現在店舗
    // （current-store-id cookie）を付け、現在店舗を受けない端点はこれを読まない。/admin 配下の画面は何も付けず、
    // /files は全店共有領域へ保存され、横断一覧が行の店舗を名指す X-Store-ID はそのまま届く。キャスト・会員の
    // portal は cookie が残っていても付けない。公開店舗サイトの匿名要求は呼び出し元が店舗を明示して渡す。
    // 受け入れの可否はバックエンドが授権店舗集合で判定する。
    try {
      // 再送は最初の要求の店舗のまま送る。待つ間に別タブで切り替わった現在店舗を付け直すと、別の店舗へ書き込む。
      const storeId = getCurrentStoreId();
      if (
        !config.authRetried &&
        getSessionKind() === 'admin' &&
        storeId &&
        isStorePath(window.location.pathname)
      ) {
        (config.headers as AxiosHeaders).set('X-Store-ID', storeId);
      }
    } catch {
      // cookie が読めない環境ではヘッダ無しで送る（受け入れの可否はバックエンドが fail-closed に判定する）
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

export type RefreshResult =
  | { status: 'ok'; refreshExpiresAt: number }
  | { status: 'unauthenticated' }
  | { status: 'credential_changed' }
  | { status: 'conflict' }
  | { status: 'error' };

/**
 * リフレッシュ cookie でアクセストークンを取り直す。成功すればメモリのトークンを置き換え、session-kind の
 * 期限をリフレッシュトークンの期限へ延ばす。GET /me は取り直さない（権限の変更は資格情報の版が増分し、
 * credential_changed で失敗する）。失敗の扱いは呼び出し側に任せる — ログイン画面はどの失敗でもフォームを出す。
 */
export async function requestRefresh(): Promise<RefreshResult> {
  const userId = getSessionUserId();
  try {
    const { data } = await apiClient.post<TokenResponse>('/auth/refresh', undefined, {
      // リフレッシュ自身の 401 を 401 経路に乗せない（乗せると再帰する）。
      skipAuthRedirect: true,
      // 名乗るのはこのタブが本人を持っているときだけ。開いた直後は cookie の本人として始まる。
      headers: userId === undefined ? undefined : { 'X-Session-User-Id': String(userId) },
    });
    storeSessionToken(data);
    const sessionKind = getSessionKind();
    if (sessionKind) startSession(sessionKind, data.refresh_expires_at);
    return { status: 'ok', refreshExpiresAt: data.refresh_expires_at };
  } catch (error) {
    const response = (error as AxiosError).response;
    if (response?.status === 409) return { status: 'conflict' };
    if (response?.status === 401) {
      return response.headers?.['x-auth-failure'] === 'credential_changed'
        ? { status: 'credential_changed' }
        : { status: 'unauthenticated' };
    }
    return { status: 'error' };
  }
}

/** ログインへ差し戻す。会員ポータルの現在地（店舗つき・フラグメントつき）は戻り先として預ける。 */
function sendToLogin(): void {
  if (getSessionAlert() === 'credential_changed') return;
  // 失効を名乗るのはトークンを持っていたときだけ（未ログインの来訪者には言わない）。
  const hadToken = getAccessToken() !== undefined;
  clearSessionToken();
  if (typeof window === 'undefined' || window.location.pathname.includes('/login')) return;
  // 画面遷移の守衛は無く、未ログイン・失効の差し戻しはここが唯一の経路。白名単の外（店舗コンソール等）は
  // rememberMemberReturnPath が黙って捨てる。フラグメントも預ける — 申領画面のように、画面が読み取る値が
  // そこにしか無い経路がある。
  rememberMemberReturnPath(
    `${window.location.pathname}${window.location.search}`,
    window.location.hash
  );
  redirectToLogin(hadToken ? 'expired' : undefined);
}

let inFlight: Promise<RefreshResult> | null = null;

/**
 * セッションを取り戻す。同時に呼ばれたものは 1 回のリフレッシュに束ねる。失敗は種類ごとに処理する:
 * 資格情報の変更と 409 は表示を出して利用者の操作を待ち、それ以外の 401 はログインへ差し戻す。
 */
export function restoreSession(): Promise<RefreshResult> {
  // 再ログインを促している間は取り直さない。credential_changed の応答はリフレッシュ cookie も消すので、
  // 取り直すと missing で失敗し、利用者がボタンを押す前にログインへ移してしまう。
  if (getSessionAlert() === 'credential_changed') {
    return Promise.resolve({ status: 'credential_changed' });
  }
  inFlight ??= requestRefresh()
    .then(result => {
      if (result.status === 'unauthenticated') sendToLogin();
      if (result.status === 'credential_changed') {
        clearSessionToken();
        setSessionAlert('credential_changed');
      }
      if (result.status === 'conflict') setSessionAlert('conflict');
      return result;
    })
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

apiClient.interceptors.response.use(
  response => response,
  async error => {
    const config = error.config;
    // 登録直後のログインや資格情報つきの招待受諾等、呼び出し元が失敗を自前で扱う経路は
    // config.skipAuthRedirect でリフレッシュ・差し戻しから除外する（リフレッシュ要求自身もここで止まる）。
    if (error.response?.status === 401 && config && !config.skipAuthRedirect) {
      const current = getAccessToken();
      if (config.authRetried) {
        sendToLogin();
      } else if (current && config.headers?.Authorization !== `Bearer ${current}`) {
        // 旧トークンで出た要求の 401 が、別の要求のリフレッシュが済んだ後に届いた。取り直さずに再送する。
        return apiClient({ ...config, authRetried: true });
      } else if ((await restoreSession()).status === 'ok') {
        return apiClient({ ...config, authRetried: true });
      }
    }
    if (error.response?.status === 403) {
      // 旧形式（ロール名・廃止済みのコンソール値）の session-kind cookie を持つ有効期限内トークンは
      // 403 を受けても 401 経路に乗らず、再ログイン導線が無いままデッドロックする。cookie 値が現行の
      // セッション種別（admin/cast/member）でない場合に限りセッションを破棄して再ログインへ促す
      // （現行のセッションで正当に 403 を受けた場合は何もしない）。
      const sessionKind = getSessionKind();
      if (
        sessionKind &&
        sessionKind !== 'admin' &&
        sessionKind !== 'cast' &&
        sessionKind !== 'member'
      ) {
        clearSessionToken();
        clearSession();
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          redirectToLogin('expired');
        }
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
