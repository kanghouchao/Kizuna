import axios from 'axios';
import Cookies from 'js-cookie';
import { clearMeCache } from './me-cache';
import {
  clearPlatformSession,
  getPlatformConsole,
  getStoreIdFromPath,
  redirectToLogin,
  setPlatformStore,
} from '@/shared/lib';

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
    // 呼び出し元が明示的に束縛した Authorization（me キャッシュの鍵と応答の対応を守る）は
    // 上書きしない。無いときだけ cookie から補う
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const csrfToken = Cookies.get('XSRF-TOKEN') || Cookies.get('X-CSRF-TOKEN');
    if (csrfToken) {
      (config.headers as any)['X-XSRF-TOKEN'] = csrfToken;
    }
    // 平台セッションがあれば legacy な x-mw ヘッダ注入をスキップする
    try {
      const platformConsole = getPlatformConsole();
      if (platformConsole) {
        const storeId = getStoreIdFromPath(window.location.pathname);
        const url = config.url || '';
        if (storeId && (url.startsWith('/store') || url.startsWith('/files'))) {
          (config.headers as any)['X-Role'] = 'store';
          (config.headers as any)['X-Store-ID'] = storeId;
        }
      } else {
        // Attach role and store context from middleware cookies
        const role = Cookies.get('x-mw-role');
        if (role) {
          (config.headers as any)['X-Role'] = role;
          if (role === 'store') {
            const storeId = Cookies.get('x-mw-store-id');
            if (storeId) {
              (config.headers as any)['X-Store-ID'] = storeId;
            }
          }
        }
      }
    } catch {
      // noop
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  response => {
    // 成功応答が X-Store-ID を伴う＝バックエンドが StoreIdInterceptor の fail-closed 検証
    //（storeBridge + scope.authorizes）を通過して受理した証拠。このときだけ「前回選択」cookie を更新し、
    // 未検証の URL 由来 id で cookie を汚染しない。
    const storeId = (response.config?.headers as any)?.['X-Store-ID'];
    if (storeId) {
      setPlatformStore(storeId);
    }
    return response;
  },
  error => {
    if (error.response?.status === 401) {
      // 招待受諾のインラインログイン等、呼び出し元が独自にセッションを扱う経路は
      // config.skipAuthRedirect でグローバルな token 除去/リダイレクトから除外する
      if ((error.config as any)?.skipAuthRedirect) {
        return Promise.reject(error);
      }
      // 要求が明示的に束縛した token（expectedToken）と異なる「非空の」token が現在の
      // cookie にあるなら、既に別セッションへ移行済み。陳腐な要求の 401 で新しいセッションを
      // 壊さない。cookie が無い（失効・除去済み）場合は移行の証拠ではないので、通常どおり
      // 後始末（キャッシュ破棄・ログインへの誘導）を行う
      const expectedToken = (error.config as any)?.expectedToken;
      const currentToken = Cookies.get('token');
      if (expectedToken && currentToken && currentToken !== expectedToken) {
        return Promise.reject(error);
      }
      Cookies.remove('token');
      // 失効・停止で終わるセッションは logout を経ないため、ここでも me キャッシュを破棄して
      // 共有端末に個人情報（氏名・権限・担当店舗）を残さない
      clearMeCache();
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        redirectToLogin();
      }
    }
    if (error.response?.status === 403) {
      // 旧形式（ロール名）の platform-role cookie を持つ有効期限内トークンは能力ベースの認可で
      // 全端点 403 になるが 401 経路に乗らず、再ログイン導線が無いままデッドロックする。
      // cookie 値が新形式のセッション種別（platform/store/cast/member）でない場合に限りセッションを破棄して
      // 再ログインへ促す（新形式のセッションで正当に 403 を受けた場合は何もしない）。
      const platformConsole = getPlatformConsole();
      if (
        platformConsole &&
        platformConsole !== 'platform' &&
        platformConsole !== 'store' &&
        platformConsole !== 'cast' &&
        platformConsole !== 'member'
      ) {
        Cookies.remove('token');
        clearMeCache();
        clearPlatformSession();
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          redirectToLogin();
        }
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
