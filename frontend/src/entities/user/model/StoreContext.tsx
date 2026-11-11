'use client';

import { usePathname } from 'next/navigation';
import { createContext, useContext, useEffect, useState } from 'react';
import { storeIdOf } from '@/shared/lib';
import { authApi } from '../api/auth';
import { AuthorizedStore } from './types';

/**
 * 店舗コンテキスト（授権店舗・現在店舗）を一手に担う deep module。管理画面シェルに1つだけ搭載し、
 * stores() を provider で1回だけ呼ぶ（消費者ごとの重複取得を無くす）。
 * 現在店舗は URL の店舗 id（storeIdOf）で、apiClient の X-Store-ID と同じ読みに従う。状態としては持たない。
 */
interface StoreContextValue {
  /** null = 読み込み中（または取得失敗）、[] = 授権店舗なし、非空 = 授権店舗一覧。 */
  stores: AuthorizedStore[] | null;
  /** URL が名指す店舗 id。店舗の文脈の画面でだけ定まり、授権店舗に含まれるかは問わない。 */
  currentStoreId: string | undefined;
  /** currentStoreId の授権店舗。一覧の取得前・授権外の店舗では無い。 */
  currentStore: AuthorizedStore | undefined;
  /**
   * 取得そのものが失敗した（通信断・5xx）。「店舗が無い」という授権の答えとは区別される。
   * 初回の失敗では stores は null のまま、取り直しの失敗では直前の一覧を保つ。
   */
  loadFailed: boolean;
  /** 授権店舗を取り直す。一覧は置き換わるまで保持し、取り直し中に店舗級ページを畳まない。 */
  reload: () => void;
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
  const currentStoreId = storeIdOf(usePathname());
  const [stores, setStores] = useState<AuthorizedStore[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    authApi.stores().then(
      list => {
        if (!active) return;
        setStores(list);
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

  const currentStore = stores?.find(store => String(store.id) === currentStoreId);

  return (
    <StoreContext.Provider value={{ stores, currentStoreId, currentStore, loadFailed, reload }}>
      {children}
    </StoreContext.Provider>
  );
}

/**
 * 店舗の文脈の画面（/store/{storeId}/**）が表示している店舗 id。シェルはこの店舗が授権店舗に含まれると
 * 確かめてから店舗の画面を描くので、店舗の画面の中では必ず定まる。それ以外の画面から呼ぶのは誤り。
 */
export function useCurrentStoreId(): string {
  const { currentStoreId } = useStoreContext();
  if (currentStoreId === undefined) {
    throw new Error('useCurrentStoreId must be used on a store page (/store/{storeId}/**)');
  }
  return currentStoreId;
}
