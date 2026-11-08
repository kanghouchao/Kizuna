'use client';

import { createContext, useContext } from 'react';

/** 利用者層の段。会員は本文 18px・押せる領域 48px 以上、キャストは本文 16px・44px 以上。 */
export type PortalTier = 'cast' | 'member';

export const PORTAL_TIER_SIZES = {
  cast: { body: 'text-base', target: 'min-h-11 min-w-11' },
  member: { body: 'text-lg', target: 'min-h-12 min-w-12' },
} as const satisfies Record<PortalTier, { body: string; target: string }>;

const PortalTierContext = createContext<PortalTier | null>(null);

/** 本人ポータルの外枠が置く。共有部品はこれで段を知り、ページごとに大きさを渡さない。 */
export const PortalTierProvider = PortalTierContext.Provider;

/** 外枠の中なら段の大きさ、管理画面など外枠の外なら null。 */
export function usePortalTierSizes() {
  const tier = useContext(PortalTierContext);
  return tier === null ? null : PORTAL_TIER_SIZES[tier];
}
