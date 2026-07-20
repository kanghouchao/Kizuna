'use client';

import { useEffect, useState } from 'react';
import { platformAuthApi } from '../api/platform';
import { hasStoreConsoleCapability } from './storeConsoleCapability';
import { PlatformStore } from './types';

/**
 * 到達資格のある授権店舗一覧を解決する共有 hook（#413 Fix7）。
 * Header の店舗切替表示と StoreSelectPage の選択肢解決が同一ロジックを共有し、
 * 「STORE_VIEW 欠如で stores() が 403 になる実運用店舗能力保持者を締め出す」バグの三度目の再発を構造的に防ぐ。
 *
 * 戻り値: null = 読み込み中、[] = アクセス可能店舗なし（能力無し/0件/フォールバック不能）、非空 = 到達資格のある一覧。
 *
 * stores()（GET /platform/stores/me）は SHARED 能力 STORE_VIEW でのみ守られる。STORE_VIEW を持たない
 * 実運用店舗能力保持者（例: STORE_PROFILE_MANAGE のみ。AuthorizationScenesIT が実証する正当な組合せ）は
 * 403 になるため、stores() の失敗と me() の成否を allSettled で独立に扱う。me()（GET /platform/me）は
 * isAuthenticated() のみで守られ常に到達可能（#413 Fix6-1）。
 */
export function useAuthorizedStores(): PlatformStore[] | null {
  const [stores, setStores] = useState<PlatformStore[] | null>(null);

  useEffect(() => {
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
        setStores(authorized);
      }
    );
    // mount 時に一度だけ解決する。router 等の外部依存は持たない純データ取得。
  }, []);

  return stores;
}
