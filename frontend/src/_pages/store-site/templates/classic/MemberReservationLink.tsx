'use client';

import type { ReactNode } from 'react';
import { useMemberReservationUrl } from '../../model/useMemberReservationUrl';

interface MemberReservationLinkProps {
  className?: string;
  children: ReactNode;
}

/** 会員ポータルの予約入口。URL はブラウザでしか決まらないので、決まるまでは出さない。 */
export default function MemberReservationLink({ className, children }: MemberReservationLinkProps) {
  const href = useMemberReservationUrl();
  if (!href) return null;
  return (
    <a href={href} className={className}>
      {children}
    </a>
  );
}
