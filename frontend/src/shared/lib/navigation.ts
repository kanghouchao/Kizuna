// 遷移の実行部を差し替え可能にしたナビゲーション（テストでは navigator を注入する）

type NavigatorFn = (url: string) => void;

function defaultNavigator(url: string) {
  if (typeof window !== 'undefined') {
    try {
      window.location.assign(url);
    } catch {
      // jsdom などページ遷移を実装しない環境では握りつぶす
    }
  }
}

let navigatorFn: NavigatorFn = defaultNavigator;

/**
 * ログイン画面のパス。理由を渡すと、着地したログイン画面がその理由を名乗る
 * （白名単の理由コード。知らない値は着地側が黙って捨てる）。
 *
 * 組み立てをここ一箇所に閉じるのは、差し戻す経路が全画面遷移（この下）と
 * クライアント遷移（AuthContext の logout）の二つに分かれているため。
 */
export function loginPath(reason?: string) {
  return reason ? `/platform/login?reason=${encodeURIComponent(reason)}` : '/platform/login';
}

/** ログイン画面へ差し戻す。理由の扱いは loginPath と同じ。 */
export function redirectToLogin(reason?: string) {
  if (typeof window !== 'undefined') {
    try {
      if (!window.location.pathname.includes('/login')) {
        navigatorFn(loginPath(reason));
      }
    } catch {
      // pathname の読み取りが例外になる環境では何もしない
    }
  }
}

export function __setNavigatorForTests(fn: NavigatorFn) {
  navigatorFn = fn;
}

export function __resetNavigator() {
  navigatorFn = defaultNavigator;
}

export default redirectToLogin;
