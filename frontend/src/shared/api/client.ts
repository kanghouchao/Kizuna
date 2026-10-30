import axios from 'axios';
import type { AxiosHeaders } from 'axios';
import Cookies from 'js-cookie';
import {
  clearSession,
  getSessionKind,
  getCurrentStoreId,
  isStorePath,
  redirectToLogin,
  rememberMemberReturnPath,
} from '@/shared/lib';

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean;
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
    const token = Cookies.get('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const csrfToken = Cookies.get('XSRF-TOKEN') || Cookies.get('X-CSRF-TOKEN');
    if (csrfToken) {
      (config.headers as AxiosHeaders).set('X-XSRF-TOKEN', csrfToken);
    }
    // 店舗文脈は X-Store-ID だけで名乗る。従業員の店舗級画面（/store 配下）からの要求には宛先を問わず現在店舗
    // （current-store-id cookie）を付け、現在店舗を受けない端点はこれを読まない。/admin 配下の画面は何も付けず、
    // /files は全店共有領域へ保存され、横断一覧が行の店舗を名指す X-Store-ID はそのまま届く。キャスト・会員の
    // portal は cookie が残っていても付けない。公開店舗サイトの匿名要求は呼び出し元が店舗を明示して渡す。
    // 受け入れの可否はバックエンドが授権店舗集合で判定する。
    try {
      const storeId = getCurrentStoreId();
      if (getSessionKind() === 'admin' && storeId && isStorePath(window.location.pathname)) {
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

apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // 招待受諾のインラインログイン等、呼び出し元が独自にセッションを扱う経路は
      // config.skipAuthRedirect でグローバルな token 除去/リダイレクトから除外する
      if (error.config?.skipAuthRedirect) {
        return Promise.reject(error);
      }
      Cookies.remove('token');
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        // 失効した MEMBER token でも画面の入口は通るため、差し戻しはここが初めてになりうる。
        // 会員ポータルの現在地（店舗つき）を覚えないと、ログイン後の戻り先を失う。
        // 白名単の外（店舗コンソール等）は rememberMemberReturnPath が黙って捨てる。
        // フラグメントも預ける — 申領画面のように、画面が読み取る値がそこにしか無い経路がある。
        rememberMemberReturnPath(
          `${window.location.pathname}${window.location.search}`,
          window.location.hash
        );
        redirectToLogin('expired');
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
        Cookies.remove('token');
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
