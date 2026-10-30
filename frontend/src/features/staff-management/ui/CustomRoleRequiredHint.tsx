'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { hasPermission, readTokenClaims } from '@/shared/lib';

/**
 * サービスID に授与できるのは自作ロールだけで、既定ロールしか無い環境では候補が必ず空になる。
 * 空の組だけでは次の手が分からないため、ロール管理での作成を案内する。
 * リンクは遷移先を開ける ROLE_MANAGE の保持者にだけ出す。
 */
export function CustomRoleRequiredHint() {
  const [canManageRoles, setCanManageRoles] = useState(false);
  useEffect(() => setCanManageRoles(hasPermission(readTokenClaims(), 'ROLE_MANAGE')), []);
  return (
    <p className="text-sm text-muted-foreground">
      サービスIDに付与できるロールがありません。先にロール管理で自作ロールを作成してください。
      {canManageRoles && (
        <>
          {' '}
          <Link href="/admin/roles" className="whitespace-nowrap text-primary-strong underline">
            ロール管理を開く
          </Link>
        </>
      )}
    </p>
  );
}
