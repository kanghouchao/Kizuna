'use client';

import { authApi, useAuth } from '@/entities/user';
import { useResource } from '@/shared/lib';
import { Button, ListRow, ListRows, RegionError, Skeleton } from '@/shared/ui';
import { PANEL } from './RemunerationDetail';

/** アカウントタブ。表示名と、サービス条件・パスワード変更・LINE連携の下位画面への入口、ログアウト。 */
export function CastAccountPage() {
  const { logout } = useAuth();
  const { data: me, isLoading, failure, reload } = useResource(() => authApi.me());

  return (
    <div className="space-y-4">
      <section aria-label="表示名" className={PANEL}>
        <p className="text-sm text-muted-foreground">表示名</p>
        {isLoading ? (
          <Skeleton role="status" aria-label="読み込み中" className="h-7 w-40" />
        ) : failure !== null ? (
          // シェルが mount 時に本人確認済みのため通常は到達しない。それでも読み込み表示の
          // まま固着させると、名前が来ないのか壊れているのか区別できず回復手段も無い
          <RegionError message="表示名を取得できませんでした" onRetry={() => void reload()} />
        ) : (
          <p className="text-lg font-semibold wrap-anywhere">{me?.display_name ?? ''}</p>
        )}
      </section>
      <nav aria-label="設定">
        <ListRows>
          <ListRow title="サービス条件" href="/cast/service-conditions" />
          <ListRow title="パスワード変更" href="/cast/account/password" />
          <ListRow title="LINE連携" href="/cast/account/line" />
        </ListRows>
      </nav>
      <Button
        type="button"
        variant="outline"
        onClick={() => logout()}
        className="min-h-11 w-full text-base"
      >
        ログアウト
      </Button>
    </div>
  );
}
