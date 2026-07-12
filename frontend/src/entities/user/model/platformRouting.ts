import { PlatformRole } from './types';

export type PlatformDestination = 'central' | 'store' | 'unsupported';

/** 平台ロールからログイン後の遷移先を解決する純関数。 */
export function resolvePlatformDestination(role: PlatformRole): PlatformDestination {
  switch (role) {
    case 'HQ_ADMIN':
      return 'central';
    case 'STORE_MANAGER':
    case 'STORE_STAFF':
      return 'store';
    case 'CAST':
    case 'MEMBER':
      return 'unsupported';
  }
}
