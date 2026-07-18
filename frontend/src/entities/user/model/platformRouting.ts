import { PlatformConsole } from './types';

export type PlatformDestination = 'central' | 'store' | 'unsupported';

/**
 * 平台コンソール（/me の console — サーバ側が能力目録から導出）からログイン後の遷移先を解決する純関数。
 * 旧形式（ロール名）の cookie 値や未知値は unsupported（fail-closed）。
 */
export function resolvePlatformDestination(console: PlatformConsole): PlatformDestination {
  switch (console) {
    case 'central':
      return 'central';
    case 'store':
      return 'store';
    default:
      return 'unsupported';
  }
}
