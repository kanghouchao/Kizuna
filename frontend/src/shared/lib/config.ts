export function getPlatformDomain(): string {
  return process.env.NEXT_PUBLIC_PLATFORM_DOMAIN || 'kizuna.test';
}

export function isStoreDomain(): boolean {
  if (typeof window === 'undefined') return false;
  return window.location.hostname !== getPlatformDomain();
}
