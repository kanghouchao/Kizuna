'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { getCurrentStoreId, setCurrentStore } from '@/shared/lib';
import { authApi } from '../api/auth';
import { AuthorizedStore } from './types';

/**
 * 店舗コンテキスト（授権店舗・現在店舗・切替）を一手に担う deep module。管理画面シェルに1つだけ搭載し、
 * stores() を provider で1回だけ呼ぶ（消費者ごとの重複取得を無くす）。
 * 現在店舗は current-store-id cookie に置く — apiClient がそこから X-Store-ID を同期に読むため、
 * cookie を書いてから state へ出す（逆順だと店舗級ページの初回取得がヘッダ無しで飛ぶ）。
 */
interface StoreContextValue {
  /** null = 読み込み中（または取得失敗）、[] = 授権店舗なし、非空 = 授権店舗一覧。 */
  stores: AuthorizedStore[] | null;
  /** 現在店舗 id。前回選択 cookie が授権店舗を指していればそれ、無ければ授権店舗の先頭。 */
  currentStoreId: string | undefined;
  currentStore: AuthorizedStore | undefined;
  /**
   * 取得そのものが失敗した（通信断・5xx）。「店舗が無い」という授権の答えとは区別される。
   * 初回の失敗では stores は null のまま、取り直しの失敗では直前の一覧を保つ。
   */
  loadFailed: boolean;
  /** 授権店舗を取り直す。一覧は置き換わるまで保持し、取り直し中に店舗級ページを畳まない。 */
  reload: () => void;
  /** 現在店舗を切り替える。遷移はしない（シェルが現在店舗をキーにページを再マウントする）。 */
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
  const [stores, setStores] = useState<AuthorizedStore[] | null>(null);
  const [currentStoreId, setCurrentStoreId] = useState<string | undefined>(undefined);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    authApi.stores().then(
      list => {
        if (!active) return;
        // cookie は利用者が任意値を書けるため、授権店舗に含まれるときだけ採る。
        const lastUsed = getCurrentStoreId();
        const resolved = list.some(store => String(store.id) === lastUsed)
          ? lastUsed
          : list[0]?.id !== undefined
            ? String(list[0].id)
            : undefined;
        if (resolved !== undefined) setCurrentStore(resolved);
        setStores(list);
        setCurrentStoreId(resolved);
      },
      (reason: unknown) => {
        if (!active) return;
        // 取得失敗を空一覧へ畳んではいけない。畳むと通信障害が「授権店舗ゼロ」の行き止まりに化ける。
        console.error('Failed to fetch stores', reason);
        setLoadFailed(true);
      }
    );
    return () => {
      active = false;
    };
  }, [loadAttempt]);

  const reload = () => {
    setLoadFailed(false);
    setLoadAttempt(count => count + 1);
  };

  const switchStore = (id: number) => {
    setCurrentStore(id);
    setCurrentStoreId(String(id));
  };

  const currentStore = stores?.find(store => String(store.id) === currentStoreId);

  return (
    <StoreContext.Provider
      value={{ stores, currentStoreId, currentStore, loadFailed, reload, switchStore }}
    >
      {children}
    </StoreContext.Provider>
  );
}
