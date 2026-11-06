'use client';

import Link from 'next/link';
import { ChevronRightIcon } from 'lucide-react';
import { useAuth } from '@/entities/user';
import { Button, Card } from '@/shared/ui';

/** アカウントタブ。設定の入口とログアウト、最下部に誤操作しにくい退会の導線を置く。 */
export function MemberAccountPage() {
  const { logout } = useAuth();

  return (
    <div className="space-y-6">
      <Card className="py-0">
        <nav aria-label="設定">
          <Link
            href="/member/notification-settings"
            className="flex min-h-12 items-center justify-between rounded-xl px-4 hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
          >
            通知の設定
            <ChevronRightIcon className="size-5 text-muted-foreground" aria-hidden="true" />
          </Link>
        </nav>
      </Card>
      <Button type="button" variant="outline" onClick={() => logout()} className="h-12 w-full">
        ログアウト
      </Button>
      <p className="text-center text-sm">
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
