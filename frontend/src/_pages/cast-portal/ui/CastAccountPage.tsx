'use client';

import { platformAuthApi, useAuth } from '@/entities/user';
import { LineLinkSection } from '@/features/line-auth';
import { useResource } from '@/shared/lib';
import { Button, Card, CardContent, RegionError } from '@/shared/ui';

/** アカウントタブ。表示名とログアウトのみの最小画面。 */
export function CastAccountPage() {
  const { logout } = useAuth();
  const { data: me, isLoading, failure, reload } = useResource(() => platformAuthApi.me());

  return (
    <div className="space-y-4 p-4">
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
      <LineLinkSection />
    </div>
  );
}
