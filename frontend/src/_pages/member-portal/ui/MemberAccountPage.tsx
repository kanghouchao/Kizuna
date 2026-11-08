'use client';

import Link from 'next/link';
import { useAuth } from '@/entities/user';
import { Button, ListRow, ListRows } from '@/shared/ui';

/** アカウントタブ。設定の一覧とログアウト、最下部に誤操作しにくい灰色の退会リンクを置く。 */
export function MemberAccountPage() {
  const { logout } = useAuth();

  return (
    <div className="space-y-6">
      <ListRows label="設定">
        <ListRow title="通知の設定" href="/member/notification-settings" />
        <ListRow title="LINE連携" href="/member/account/line" />
      </ListRows>
      <Button
        type="button"
        variant="outline"
        onClick={() => logout()}
        className="h-12 w-full text-lg"
      >
        ログアウト
      </Button>
      <p className="text-center">
        <Link
          href="/member/withdrawal"
          className="inline-flex min-h-12 min-w-12 items-center justify-center text-muted-foreground underline underline-offset-4 hover:text-foreground"
        >
          退会する
        </Link>
      </p>
    </div>
  );
}
