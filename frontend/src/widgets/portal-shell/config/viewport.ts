import type { Viewport } from 'next';

// env(safe-area-inset-*) は viewport-fit=cover のときだけ 0 以外になり、外枠の題名帯と下部タブがそれを読む
export const portalViewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};
