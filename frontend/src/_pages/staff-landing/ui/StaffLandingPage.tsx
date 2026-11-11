'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useLandingPath } from '@/widgets/admin-shell';

/** 何も選んでいない従業員の着地。メニューの木と授権店舗が揃ったら一階層目の先頭のダッシュボードへ置き換える。 */
export default function StaffLandingPage() {
  const router = useRouter();
  const landing = useLandingPath();

  useEffect(() => {
    if (landing !== undefined) router.replace(landing);
  }, [landing, router]);

  return <p className="text-sm text-muted-foreground">読み込み中...</p>;
}
