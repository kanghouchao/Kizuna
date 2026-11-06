import type { Viewport } from 'next';

export { CastPortalLayout as default } from '@/_pages/cast-portal';

// env(safe-area-inset-*) は viewport-fit=cover のときだけ 0 以外になる
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};
