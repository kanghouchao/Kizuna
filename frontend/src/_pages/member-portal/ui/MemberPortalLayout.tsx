'use client';

import { CalendarCheckIcon, CircleUserRoundIcon, CoinsIcon, HomeIcon } from 'lucide-react';
import { MeProvider } from '@/entities/user';
import { PortalShell, type PortalScreens, type PortalTab } from '@/widgets/portal-shell';
import { EmailVerificationBanner } from './EmailVerificationBanner';

const TABS: readonly PortalTab[] = [
  { href: '/member', label: 'ホーム', icon: HomeIcon },
  { href: '/member/reservations', label: '予約', icon: CalendarCheckIcon },
  { href: '/member/points', label: 'ポイント', icon: CoinsIcon },
  { href: '/member/account', label: 'アカウント', icon: CircleUserRoundIcon },
];

export const MEMBER_SCREENS: PortalScreens = {
  '/member': { title: 'ホーム', tab: '/member' },
  '/member/reservations': { title: '予約', tab: '/member/reservations' },
  '/member/reservations/new': { title: '予約申請', back: '/member/reservations' },
  '/member/points': { title: 'ポイント', tab: '/member/points' },
  '/member/visits': { title: '来店履歴', back: '/member/points' },
  '/member/receipts': { title: '伝票の取り込み', back: '/member/points' },
  '/member/account': { title: 'アカウント', tab: '/member/account' },
  '/member/notification-settings': { title: '通知の設定', back: '/member/account' },
  '/member/withdrawal': { title: '退会', back: '/member/account' },
};

/** 会員ポータルの外枠。/me を一度だけ取り、全画面の同じ位置に未検証の案内を出す。 */
export function MemberPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <MeProvider>
      <PortalShell
        tier="member"
        tabs={TABS}
        screens={MEMBER_SCREENS}
        notice={<EmailVerificationBanner />}
      >
        {children}
      </PortalShell>
    </MeProvider>
  );
}
