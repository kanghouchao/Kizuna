'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import {
  getPlatformStoreId,
  getStoreIdFromPath,
  isPlatformSession,
  readTokenClaims,
  replaceStoreIdInPath,
  setPlatformStore,
} from '@/shared/lib';
import { platformAuthApi } from '../api/platform';
import { PlatformStore } from './types';

/**
 * 店舗コンテキスト（現在店舗・授権店舗・切替・ログイン後着地の授権店舗解決）を一手に担う deep module。
 * 両コンソール layout に1つだけ搭載し、Header / StoreEntryPage が共有状態を消費する。
 * 資格（storeBridge）は token claim から同期に読み、stores() だけを provider で1回呼ぶ
 * （消費者ごとの重複取得を無くす）。
 */
interface StoreContextValue {
  /** null = 読み込み中、[] = 到達資格のある店舗なし、非空 = 授権店舗一覧。 */
  stores: PlatformStore[] | null;
  /** 店舗コンソール資格（token claim の storeBridge）。null = 読み込み中。 */
  storeBridge: boolean | null;
  /** 表示に用いる現在店舗 id。pathname 由来を最優先し、無ければ前回選択 cookie。 */
  currentStoreId: string | undefined;
  /**
   * 取得そのものが失敗した（通信断・5xx）。true の間 stores / storeBridge は null のままで、
   * 「資格が無い」「店舗が無い」という授権の答えとは区別される。
   */
  loadFailed: boolean;
  /** 失敗した取得をやり直す。 */
  reload: () => void;
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
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  // cookie 由来の「前回選択した店舗」ヒント。document 依存で SSR-unsafe なため mount 時のみ読む。
  // store-scoped ページ外（platform 側）に居るときだけ表示に使う fallback 専用の役割。
  const [lastUsedStoreId, setLastUsedStoreId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (isPlatformSession()) {
      setLastUsedStoreId(getPlatformStoreId());
    }
  }, []);

  useEffect(() => {
    // 店舗コンソール資格は token claim（storeBridge）から同期に読む。値は現在の cookie の
    // token から導出され、取り違えも失効管理も無い（token 無し・壊れは資格なし側に倒れ、
    // その利用者は最初の API 呼び出しの 401 でログインへ誘導される）。
    // storeBridge=false（SHARED/標識のみ/PLATFORM のみ）は stores() を呼ばず空一覧扱い。
    // storeBridge=true のみ stores()（GET /platform/stores/me）で店名付き一覧を取得する。
    const bridge = readTokenClaims()?.storeBridge === true;
    setStoreBridge(bridge);
    if (!bridge) {
      setStores([]);
      return;
    }
    platformAuthApi.stores().then(setStores, (reason: unknown) => {
      // 取得失敗を空一覧へ畳んではいけない。畳むと通信障害が「授権店舗ゼロ」
      // （入口がセッションを破棄する）に化ける。値は未確定のまま旗だけ立てる。
      console.error('Failed to fetch stores', reason);
      setLoadFailed(true);
    });
  }, [loadAttempt]);

  const reload = () => {
    setLoadFailed(false);
    setStores(null);
    setStoreBridge(null);
    setLoadAttempt(count => count + 1);
  };

  // 表示する店舗は pathname 由来の storeId を最優先し、無ければ cookie ヒントに fallback する。
  // usePathname() は hydration-safe なため毎レンダー再計算でき、店舗切替後の pathname 変化にラベルが追随する。
  const pathStoreId = getStoreIdFromPath(pathname);
  const currentStoreId = pathStoreId ?? lastUsedStoreId;

  const switchStore = (id: number) => {
    // no-op 判定は pathStoreId（URL が実際にその店舗 id を持つ場合）のみで行う。
    // currentStoreId（cookie fallback 込み）で比較すると、/platform 側で前回選択 cookie と
    // 同じ店舗をクリックした単一店舗ユーザーが遷移できなくなる。
    if (String(id) !== pathStoreId) {
      setPlatformStore(id);
      // 現在地に storeId を差し替えて遷移する（フルリロードはしない）。
      // store-scoped ページ外に居れば入口ルートへ送り、着地先はそちらが解決する。
      router.push(replaceStoreIdInPath(pathname, id));
    }
  };

  return (
    <StoreContext.Provider
      value={{ stores, storeBridge, currentStoreId, loadFailed, reload, switchStore }}
    >
      {children}
    </StoreContext.Provider>
  );
}
