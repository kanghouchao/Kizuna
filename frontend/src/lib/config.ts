export function getCentralDomain(): string {
  return process.env.NEXT_PUBLIC_CENTRAL_DOMAIN || 'cms.test';
}

export function isTenantDomain(): boolean {
  if (typeof window === 'undefined') return false;
  return window.location.hostname !== getCentralDomain();
}
