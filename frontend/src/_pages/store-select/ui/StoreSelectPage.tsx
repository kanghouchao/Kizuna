'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { BuildingStorefrontIcon } from '@heroicons/react/24/outline';
import { hasStoreConsoleCapability, platformAuthApi, PlatformStore } from '@/entities/user';
import { replaceStoreIdInPath, setPlatformStore } from '@/shared/lib';

/** クエリ next（店舗スコープの遷移先テンプレート）を読む。無ければダッシュボード。 */
function resolveNext(): string {
  return new URLSearchParams(window.location.search).get('next') || '/store/dashboard';
}

/**
 * 店舗未選択のまま店舗スコープ機能へ入ろうとした時の懒惰トリガー選択画面（#413）。
 * 授権店舗が1件なら選択UIを出さず自動選択して遷移し、複数件なら一覧から選ばせる。
 * 選択値は「前回選択」のUXヒントであり、認可はバックエンドの fail-closed 検証に委ねる。
 */
export default function StoreSelectPage() {
  const router = useRouter();
  // null = 取得前（読み込み中 / 1件時の自動遷移中）、[] = 0件、複数 = 選択待ち
  const [stores, setStores] = useState<PlatformStore[] | null>(null);

  const goTo = (id: number) => {
    setPlatformStore(id);
    router.replace(replaceStoreIdInPath(resolveNext(), id));
  };

  useEffect(() => {
    Promise.all([platformAuthApi.stores(), platformAuthApi.me()])
      .then(([list, me]) => {
        // 実運用の store-console 能力が無いユーザーは stores() が非空でも到達資格が無く、
        // 自動遷移/選択の末に StoreIdInterceptor で 403 になる。能力無しは空一覧扱い（#413 Fix5-3）。
        const authorized = hasStoreConsoleCapability(me.capabilities) ? list : [];
        if (authorized.length === 1) {
          goTo(authorized[0].id);
          return;
        }
        setStores(authorized);
      })
      .catch(error => {
        console.error('Failed to fetch stores', error);
        setStores([]);
      });
    // router は安定参照。goTo は resolveNext を都度読むため依存不要。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (stores === null) {
    return (
      <div className="mx-auto max-w-md">
        <p className="text-sm text-gray-500">読み込み中...</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="rounded-[10px] border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-bold text-gray-900">店舗を選択</h1>
        {stores.length === 0 ? (
          <p className="mt-4 text-sm text-gray-600">アクセス可能な店舗がありません</p>
        ) : (
          <>
            <p className="mt-1 text-sm text-gray-500">業務を行う店舗を選んでください。</p>
            <div className="mt-6 space-y-2">
              {stores.map(store => (
                <button
                  key={store.id}
                  onClick={() => goTo(store.id)}
                  className="flex w-full items-center gap-3 rounded-[10px] border border-gray-200 px-4 py-3 text-left text-sm font-medium text-gray-900 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50"
                >
                  <BuildingStorefrontIcon className="h-5 w-5 text-gray-400" />
                  {store.name}
                </button>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
