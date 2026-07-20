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
    // stores() は SHARED 能力 STORE_VIEW でのみ守られる。STORE_VIEW を持たない実運用店舗能力保持者
    //（例: STORE_PROFILE_MANAGE のみ）は 403 になるため、stores() の失敗と me() の成否を allSettled で
    // 独立に扱う。me() は isAuthenticated() のみで守られ常に到達可能（#413 Fix6-1）。
    Promise.allSettled([platformAuthApi.stores(), platformAuthApi.me()]).then(
      ([storesResult, meResult]) => {
        if (meResult.status !== 'fulfilled') {
          // me() 失敗はセッション異常（401 は apiClient が再ログインへ誘導）。ここでは空一覧扱い。
          console.error('Failed to fetch me', meResult.reason);
          setStores([]);
          return;
        }
        const me = meResult.value;
        // 実運用の store-console 能力が無いユーザーは stores() が非空でも到達資格が無く、
        // 自動遷移/選択の末に StoreIdInterceptor で 403 になる。能力無しは空一覧扱い（#413 Fix5-3）。
        if (!hasStoreConsoleCapability(me.capabilities)) {
          setStores([]);
          return;
        }
        // stores() が成功すれば店名付き一覧を使う。403 で失敗した場合は /platform/me の store_ids を
        // フォールバック源にする（SPECIFIC_STORES 時のみ非空。店名は取れないため「店舗 #id」で表示）。
        // 既知の残存ギャップ: store_scope_type === 'ALL_STORES' かつ STORE_VIEW 欠如の組合せは、
        // /platform/me の store_ids が空（PlatformUser のバリデーション制約 — ALL_STORES は個別店舗を持たない）
        // のためフォールバックできず「アクセス可能な店舗がありません」表示のままとなる（0 件時挙動と同じ・退行なし）。
        // 真の解決にはバックエンドの能力モデル調整（STORE_VIEW を他の Console.STORE 能力へ暗黙付与する等）が要り、
        // B-lite の「バックエンド無改修」方針の範囲外のため別途裁定が必要（#413 Fix6-1 決定ログ）。
        const authorized: PlatformStore[] =
          storesResult.status === 'fulfilled'
            ? storesResult.value
            : me.store_scope_type === 'SPECIFIC_STORES'
              ? me.store_ids.map(id => ({ id, name: `店舗 #${id}` }))
              : [];
        if (authorized.length === 1) {
          goTo(authorized[0].id);
          return;
        }
        setStores(authorized);
      }
    );
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
