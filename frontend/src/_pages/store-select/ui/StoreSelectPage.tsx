'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { BuildingStorefrontIcon } from '@heroicons/react/24/outline';
import { useStoreContext } from '@/entities/user';
import { replaceStoreIdInPath, setPlatformStore } from '@/shared/lib';
import { Button, Card } from '@/shared/ui';

/** クエリ next（店舗スコープの遷移先テンプレート）を読む。無ければダッシュボード。 */
function resolveNext(): string {
  return new URLSearchParams(window.location.search).get('next') || '/store/dashboard';
}

/**
 * 店舗未選択のまま店舗スコープ機能へ入ろうとした時の懒惰トリガー選択画面。
 * 授権店舗が1件なら選択UIを出さず自動選択して遷移し、複数件なら一覧から選ばせる。
 * 授権店舗の解決（me()+stores() の呼出し）は Header と共有の店舗コンテキスト（両コンソール
 * layout に搭載）に委ねる。
 * 選択値は「前回選択」のUXヒントであり、認可はバックエンドの fail-closed 検証に委ねる。
 */
export default function StoreSelectPage() {
  const router = useRouter();
  // null = 読み込み中（1件時の自動遷移中も含む）、[] = 0件、複数 = 選択待ち。
  const { stores } = useStoreContext();

  const goTo = (id: number) => {
    setPlatformStore(id);
    router.replace(replaceStoreIdInPath(resolveNext(), id));
  };

  useEffect(() => {
    // 授権店舗が1件なら選択UIを出さず自動選択して遷移する。
    if (stores?.length === 1) {
      goTo(stores[0].id);
    }
    // goTo は resolveNext を都度読むため依存不要。router は安定参照。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stores]);

  // 読み込み中、または1件時の自動遷移中は選択UIを出さずローディング表示のみとする。
  if (stores === null || stores.length === 1) {
    return (
      <div className="mx-auto max-w-md">
        <p className="text-sm text-muted-foreground">読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <Card className="gap-0 p-6">
        <h1 className="text-2xl font-bold text-foreground">店舗を選択</h1>
        {stores.length === 0 ? (
          <p className="mt-4 text-sm text-muted-foreground">アクセス可能な店舗がありません</p>
        ) : (
          <>
            <p className="mt-1 text-sm text-muted-foreground">業務を行う店舗を選んでください。</p>
            <div className="mt-6 space-y-2">
              {stores.map(store => (
                <Button
                  key={store.id}
                  variant="outline"
                  onClick={() => goTo(store.id)}
                  className="h-auto w-full justify-start gap-3 px-4 py-3 text-left"
                >
                  <BuildingStorefrontIcon className="size-5 text-muted-foreground" />
                  {store.name}
                </Button>
              ))}
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
