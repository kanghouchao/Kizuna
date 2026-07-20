import axios from 'axios';
import Cookies from 'js-cookie';
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
    if (token) {
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
    // 未検証の URL 由来 id で cookie を汚染しない（Sidebar の無条件 mount 書き込みを置き換える — #413 Fix6-3）。
    const storeId = (response.config?.headers as any)?.['X-Store-ID'];
    if (storeId) {
      setPlatformStore(storeId);
    }
    return response;
  },
  error => {
    if (error.response?.status === 401) {
      // 招待受諾のインラインログイン等、呼び出し元が独自にセッションを扱う経路は
      // config.skipAuthRedirect でグローバルな token 除去/リダイレクトから除外する（#327 codex指摘）
      if ((error.config as any)?.skipAuthRedirect) {
        return Promise.reject(error);
      }
      Cookies.remove('token');
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        redirectToLogin();
      }
    }
    if (error.response?.status === 403) {
      // 旧形式（ロール名）の platform-role cookie を持つ有効期限内トークンは #398 の能力化で
      // 全端点 403 になるが 401 経路に乗らず、再ログイン導線が無いままデッドロックする。
      // cookie 値がコンソール値（platform/store）でない場合に限りセッションを破棄して再ログインへ促す
      //（新形式のセッションで正当に 403 を受けた場合は何もしない）。
      const platformConsole = getPlatformConsole();
      if (platformConsole && platformConsole !== 'platform' && platformConsole !== 'store') {
        Cookies.remove('token');
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
