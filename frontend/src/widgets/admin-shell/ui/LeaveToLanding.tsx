'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef } from 'react';
import { MePending, type PermissionCode, hasPermission, useMe } from '@/entities/user';
import { notify } from '@/shared/notify';
import { useLandingPath } from '../model/landing';

const PERMISSION_DENIED = 'この画面を開く権限がありません';

/**
 * 開けない画面は描かずに一階層目の先頭の着地へ置き換え、移った先で警告を出す。開けないのが着地そのもの
 * なら移ると同じ画面へ戻り続けるので、その場で開けないと示す。StrictMode の二重実行でも移動と警告は一度だけ。
 */
export function LeaveToLanding({ message }: { message: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const landing = useLandingPath();
  const left = useRef(false);
  const isLanding = landing === pathname;

  useEffect(() => {
    if (landing === undefined || isLanding || left.current) return;
    left.current = true;
    notify.warning(message);
    router.replace(landing);
  }, [landing, isLanding, message, router]);

  if (isLanding) return <p role="alert">{message}</p>;
  return <p role="status">読み込み中...</p>;
}

/** 権限の無い画面。ページ自身の権限判定が拒んだときに中身の代わりに描く。 */
export function DeniedPage() {
  return <LeaveToLanding message={PERMISSION_DENIED} />;
}

/** ページ全体が要する権限（全てを要する）。本人情報が揃うまでは中身も移動も出さない。 */
export function PermissionGate({
  codes,
  children,
}: {
  codes: PermissionCode[];
  children: React.ReactNode;
}) {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (!codes.every(code => hasPermission(me, code))) return <DeniedPage />;
  return children;
}
