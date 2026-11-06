'use client';

import Link from 'next/link';
import { ChevronRightIcon } from 'lucide-react';
import { authApi, useAuth } from '@/entities/user';
import { LineLinkSection } from '@/features/line-auth';
import { useResource } from '@/shared/lib';
import { Button, Card, CardContent, RegionError } from '@/shared/ui';

/** アカウントタブ。表示名・ログアウト・サービス条件の入口・LINE 連携。 */
export function CastAccountPage() {
  const { logout } = useAuth();
  const { data: me, isLoading, failure, reload } = useResource(() => authApi.me());

  return (
    <div className="space-y-4">
      <Card>
        <CardContent>
          <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            表示名
          </p>
          {isLoading ? (
            <p className="mt-1 text-lg font-semibold text-foreground">読み込み中...</p>
          ) : failure !== null ? (
            // シェルが mount 時に本人確認済みのため通常は到達しない。それでも読み込み表示の
            // まま固着させると、名前が来ないのか壊れているのか区別できず回復手段も無い
            <RegionError
              message="表示名を取得できませんでした"
              onRetry={() => void reload()}
              className="mt-1"
            />
          ) : (
            <p className="mt-1 text-lg font-semibold text-foreground">{me?.display_name ?? ''}</p>
          )}
          <Button type="button" variant="outline" onClick={() => logout()} className="mt-6 w-full">
            ログアウト
          </Button>
        </CardContent>
      </Card>
      <Card className="py-0">
        <nav aria-label="設定">
          <Link
            href="/cast/service-conditions"
            className="flex min-h-11 items-center justify-between rounded-xl px-4 hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
          >
            サービス条件
            <ChevronRightIcon className="size-5 text-muted-foreground" aria-hidden="true" />
          </Link>
        </nav>
      </Card>
      <LineLinkSection />
    </div>
  );
}
