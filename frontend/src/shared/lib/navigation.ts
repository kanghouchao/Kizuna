// navigation helper with injectable navigator for easier testing

type NavigatorFn = (url: string) => void;

function defaultNavigator(url: string) {
  if (typeof window !== 'undefined') {
    try {
      window.location.assign(url);
    } catch {
      // jsdom may not implement navigation; swallow in environments where it's unsupported
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
      // reading pathname may throw in some environments; ignore
    }
  }
}

// Test helpers
export function __setNavigatorForTests(fn: NavigatorFn) {
  navigatorFn = fn;
}

export function __resetNavigator() {
  navigatorFn = defaultNavigator;
}

export default redirectToLogin;
