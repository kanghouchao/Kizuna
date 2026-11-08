'use client';

import { CalendarDaysIcon, CircleUserRoundIcon, HomeIcon, ReceiptTextIcon } from 'lucide-react';
import { PortalShell, type PortalScreens, type PortalTab } from '@/widgets/portal-shell';
import { CastHomeProvider, useCastHomeSource } from '../model/castHome';

const TABS: readonly PortalTab[] = [
  { href: '/cast', label: 'ホーム', icon: HomeIcon },
  {
    href: '/cast/schedule',
    label: 'スケジュール',
    icon: CalendarDaysIcon,
    segments: [
      { href: '/cast/schedule', label: '出勤予定' },
      { href: '/cast/requests', label: '希望提出' },
    ],
  },
  {
    href: '/cast/remunerations',
    label: '報酬',
    icon: ReceiptTextIcon,
    segments: [
      { href: '/cast/remunerations', label: '明細' },
      { href: '/cast/remunerations/monthly', label: '月次' },
    ],
  },
  { href: '/cast/account', label: 'アカウント', icon: CircleUserRoundIcon },
];

export const CAST_SCREENS: PortalScreens = {
  '/cast': { title: 'ホーム', tab: '/cast' },
  '/cast/schedule': { title: 'スケジュール', tab: '/cast/schedule' },
  '/cast/requests': { title: 'スケジュール', tab: '/cast/schedule' },
  '/cast/remunerations': { title: '報酬', tab: '/cast/remunerations' },
  '/cast/remunerations/monthly': { title: '報酬', tab: '/cast/remunerations' },
  '/cast/remunerations/[orderId]': { title: '報酬明細', back: '/cast/remunerations' },
  '/cast/account': { title: 'アカウント', tab: '/cast/account' },
  '/cast/account/password': { title: 'パスワード変更', back: '/cast/account' },
  '/cast/account/line': { title: 'LINE連携', back: '/cast/account' },
  '/cast/service-conditions': { title: 'サービス条件', back: '/cast/account' },
};

/** キャストポータルの外枠。MeProvider を持たない（共有 widget は能力を props で受ける）。 */
export function CastPortalLayout({ children }: { children: React.ReactNode }) {
  const home = useCastHomeSource();
  return (
    <CastHomeProvider value={home}>
      <PortalShell
        tier="cast"
        tabs={TABS}
        screens={CAST_SCREENS}
        badges={{ '/cast': home.pendingCount }}
      >
        {children}
      </PortalShell>
    </CastHomeProvider>
  );
}
