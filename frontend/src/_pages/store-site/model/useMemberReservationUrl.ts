'use client';

import { useEffect, useState } from 'react';
import { appOrigin, useAppDomain } from '@/shared/lib';
import { memberReservationUrl } from '../lib/memberReservation';

/** 会員ポータルの予約入口の URL。訪問中のドメインはブラウザでしか分からないため、マウントまでは null。 */
export function useMemberReservationUrl(): string | null {
  const appDomain = useAppDomain();
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    setUrl(memberReservationUrl(appOrigin(appDomain), window.location.hostname));
  }, [appDomain]);

  return url;
}
