'use client';

import { useEffect, useState } from 'react';
import { platformAuthApi, useAuth } from '@/entities/user';
import { Button, Card, CardContent } from '@/shared/ui';

/** アカウントタブ。表示名とログアウトのみの最小画面。 */
export function CastAccountPage() {
  const { logout } = useAuth();
  const [displayName, setDisplayName] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    platformAuthApi
      .me()
      .then(me => {
        if (!cancelled) setDisplayName(me.display_name);
      })
      .catch(() => {
        // シェルが mount 時に本人確認済みのため通常は到達しない。表示名は未取得のまま留める。
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="p-4">
      <Card>
        <CardContent>
          <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            表示名
          </p>
          <p className="mt-1 text-lg font-semibold text-foreground">
            {displayName ?? '読み込み中...'}
          </p>
          <Button type="button" variant="outline" onClick={logout} className="mt-6 w-full">
            ログアウト
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
