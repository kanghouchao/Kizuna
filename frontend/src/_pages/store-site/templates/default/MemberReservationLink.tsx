'use client';

import type { ReactNode } from 'react';
import { useMemberReservationUrl } from '../../model/useMemberReservationUrl';

/** 会員ポータルの予約入口へのリンク。URL が決まるまでは出さない。 */
export default function MemberReservationLink({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  const url = useMemberReservationUrl();
  if (!url) return null;
  return (
    <a href={url} className={className}>
      {children}
    </a>
  );
}
