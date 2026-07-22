'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import {
  getPlatformStoreId,
  getStoreIdFromPath,
  isPlatformSession,
  replaceStoreIdInPath,
  setPlatformStore,
} from '@/shared/lib';
import { platformAuthApi } from '../api/platform';
import { PlatformStore } from './types';

/**
 * 店舗コンテキスト（現在店舗・授権店舗・切替・ログイン後着地の授権店舗解決）を一手に担う deep module（#428）。
 * 両コンソール layout に1つだけ搭載し、Header / StoreSelectPage が共有状態を消費する。
 * me()+stores() は provider で1回のみ呼ばれる（消費者ごとの重複取得を無くす — #413 の分散を収編）。
 */
interface StoreContextValue {
  /** null = 読み込み中、[] = 到達資格のある店舗なし、非空 = 授権店舗一覧。 */
  stores: PlatformStore[] | null;
  /** 店舗コンソール資格（/me の store_bridge）。null = 読み込み中。 */
  storeBridge: boolean | null;
  /** 表示に用いる現在店舗 id。pathname 由来を最優先し、無ければ前回選択 cookie。 */
  currentStoreId: string | undefined;
  /** 店舗を切り替える（前回選択 cookie 更新 + 現在地の storeId 差し替え遷移）。 */
  switchStore: (id: number) => void;
}

const StoreContext = createContext<StoreContextValue | undefined>(undefined);

export function useStoreContext(): StoreContextValue {
  const context = useContext(StoreContext);
  if (context === undefined) {
    throw new Error('useStoreContext must be used within a StoreContextProvider');
  }
  return context;
}

export function StoreContextProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();

  // null = 読み込み中、[] = 到達資格のある店舗なし、非空 = 授権店舗一覧。
  const [stores, setStores] = useState<PlatformStore[] | null>(null);
  const [storeBridge, setStoreBridge] = useState<boolean | null>(null);
  // cookie 由来の「前回選択した店舗」ヒント。document 依存で SSR-unsafe なため mount 時のみ読む。
  // store-scoped ページ外（platform 側）に居るときだけ表示に使う fallback 専用の役割（#413 Fix1）。
  const [lastUsedStoreId, setLastUsedStoreId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (isPlatformSession()) {
      setLastUsedStoreId(getPlatformStoreId());
    }
  }, []);

  useEffect(() => {
    // まず me()（GET /platform/me・isAuthenticated() のみで守られ常に到達可能）で店舗コンソール資格を確認する。
    // store_bridge=false（SHARED/標識のみ/PLATFORM のみ）は stores() を呼ばず空一覧扱い。
    // store_bridge=true のみ stores()（緩和後の GET /platform/stores/me）で店名付き一覧を取得する。
    // #413 の fallback 梯子（店舗 #id プレースホルダ・ALL_STORES 既知欠口）はバックエンド守衛緩和で不要になり撤去した。
    platformAuthApi.me().then(
      async me => {
        setStoreBridge(me.store_bridge);
        if (!me.store_bridge) {
          setStores([]);
          return;
        }
        try {
          setStores(await platformAuthApi.stores());
        } catch (error) {
          console.error('Failed to fetch stores', error);
          setStores([]);
        }
      },
      reason => {
        // me() 失敗はセッション異常（401 は apiClient が再ログインへ誘導）。ここでは空一覧・資格なし扱い。
        console.error('Failed to fetch me', reason);
        setStoreBridge(false);
        setStores([]);
      }
    );
    // mount 時に一度だけ解決する純データ取得。
  }, []);

  // 表示する店舗は pathname 由来の storeId を最優先し、無ければ cookie ヒントに fallback する（#413 Fix1）。
  // usePathname() は hydration-safe なため毎レンダー再計算でき、店舗切替後の pathname 変化にラベルが追随する。
  const pathStoreId = getStoreIdFromPath(pathname);
  const currentStoreId = pathStoreId ?? lastUsedStoreId;

  const switchStore = (id: number) => {
    // no-op 判定は pathStoreId（URL が実際にその店舗 id を持つ場合）のみで行う。
    // currentStoreId（cookie fallback 込み）で比較すると、/platform 側で前回選択 cookie と
    // 同じ店舗をクリックした単一店舗ユーザーが遷移できなくなる（#413 Fix5-1）。
    if (String(id) !== pathStoreId) {
      setPlatformStore(id);
      // console 由来の reload をやめ、現在地に storeId を差し替えて遷移する（#413）。
      // store-scoped ページ外に居れば /store/{id}/dashboard へ。
      router.push(replaceStoreIdInPath(pathname, id));
    }
  };

  return (
    <StoreContext.Provider value={{ stores, storeBridge, currentStoreId, switchStore }}>
      {children}
    </StoreContext.Provider>
  );
}
