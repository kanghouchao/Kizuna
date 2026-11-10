'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { loginReturnTarget, withNext } from '@/shared/lib';

/**
 * ログイン画面と会員登録画面を行き来するリンク。今の URL が持ち越している戻り先（next と断片）を引き継ぐ。
 * 断片はサーバから読めないので、href はマウント後に組み直す。
 */
export function ReturnCarryingLink({
  href,
  className,
  children,
}: {
  href: string;
  className?: string;
  children: React.ReactNode;
}) {
  const [target, setTarget] = useState(href);

  useEffect(() => {
    setTarget(withNext(href, loginReturnTarget()));
  }, [href]);

  return (
    <Link href={target} className={className}>
      {children}
    </Link>
  );
}
