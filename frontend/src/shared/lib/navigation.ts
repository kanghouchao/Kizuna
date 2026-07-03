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

export function redirectToLogin() {
  if (typeof window !== 'undefined') {
    try {
      if (!window.location.pathname.includes('/login')) {
        navigatorFn('/login');
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
