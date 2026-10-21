import axios from 'axios';
import type { AxiosHeaders } from 'axios';
import Cookies from 'js-cookie';
import {
  clearPlatformSession,
  getPlatformSessionKind,
  getPlatformStoreId,
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
    // 店舗文脈は X-Store-ID だけで名乗る。管理画面の店舗級ページ（/store 配下）からの店舗級端点にだけ、
    // StoreContext が確定させた現在店舗（platform-store-id cookie）を付ける。/platform 配下の画面からの
    // /files 等は店舗文脈を持たない（全店共有領域への保存）。キャスト・会員の portal は cookie が残っていても
    // 付けない。公開店舗サイトでは proxy が焼いた x-mw-store-id を付ける。受否はバックエンドが授権店舗集合で判定する。
    try {
      const sessionKind = getPlatformSessionKind();
      if (sessionKind) {
        const storeId = getPlatformStoreId();
        const url = config.url || '';
        if (
          sessionKind === 'admin' &&
          storeId &&
          window.location.pathname.startsWith('/store') &&
          (url.startsWith('/store') || url.startsWith('/files'))
        ) {
          (config.headers as AxiosHeaders).set('X-Store-ID', storeId);
        }
      } else {
        const storeId = Cookies.get('x-mw-store-id');
        if (storeId) {
          (config.headers as AxiosHeaders).set('X-Store-ID', storeId);
        }
      }
    } catch {
      // cookie が読めない環境ではヘッダ無しで送る（受否はバックエンドが fail-closed に判定する）
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
      // 旧形式（ロール名・廃止済みのコンソール値）の platform-role cookie を持つ有効期限内トークンは
      // 403 を受けても 401 経路に乗らず、再ログイン導線が無いままデッドロックする。cookie 値が現行の
      // セッション種別（admin/cast/member）でない場合に限りセッションを破棄して再ログインへ促す
      // （現行のセッションで正当に 403 を受けた場合は何もしない）。
      const sessionKind = getPlatformSessionKind();
      if (
        sessionKind &&
        sessionKind !== 'admin' &&
        sessionKind !== 'cast' &&
        sessionKind !== 'member'
      ) {
        Cookies.remove('token');
        clearPlatformSession();
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          redirectToLogin('expired');
        }
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
