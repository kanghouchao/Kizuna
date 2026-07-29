'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'react-hot-toast';
import { MenuVO, menuApi } from '@/entities/menu';
import { useAuth, useStoreContext } from '@/entities/user';
import { getPlatformStoreId, resolveStoreHref, setPlatformStore } from '@/shared/lib';
import { Button } from '@/shared/ui';

/** メニュー木を先行順に辿り、最初の店舗スコープの葉（path 付き）を返す。 */
function firstStorePath(menus: MenuVO[]): string | undefined {
  for (const menu of menus) {
    // 統合メニューは両コンソールの節を返し、プラットフォーム節の方が先に並ぶ。
    // /store 前綴で絞らないと混成ロールの利用者が /platform/dashboard へ吸われ、店舗へ入る意図が消える。
    if (menu.path?.startsWith('/store')) return menu.path;
    const nested = firstStorePath(menu.items ?? []);
    if (nested) return nested;
  }
  return undefined;
}

/**
 * クエリ next（店舗スコープの遷移先テンプレート）を読む。
 * next は利用者が任意に書ける値なので、店舗スコープの相対パス以外は捨てる。
 * '//evil.example' や 'https://…' をそのまま遷移先にすると外部サイトへの誘導になる。
 */
function readNext(): string | undefined {
  const raw = new URLSearchParams(window.location.search).get('next');
  if (!raw || !raw.startsWith('/store') || raw.startsWith('//')) return undefined;
  return raw;
}

/**
 * 店舗コンソールの入口。UI を持たない中継点で、
 * 「授権店舗を選ぶ → 到達可能な最初の画面を決める → そこへ差し替え遷移する」だけを行う。
 *
 * 到達経路は3つ: ログイン直後・レガシーな id 無し店舗 URL の収容・コンソール/エリア不一致の差し戻し。
 * 着地先をメニュー由来にするのは、権限を絞ったカスタムロールでも必ず自分が見られる画面に着くため
 * （固定の着地先だと権限次第で 403 の白画面になる）。
 */
export default function StoreEntryPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const { stores, storeBridge } = useStoreContext();
  const resolved = useRef(false);
  // メニュー取得が失敗した回。再試行のたびに増やして解決を再走させる。
  const [attempt, setAttempt] = useState(0);
  const [fetchFailed, setFetchFailed] = useState(false);

  useEffect(() => {
    // stores / storeBridge の null は読み込み中。
    if (resolved.current || stores === null || storeBridge === null) return;
    // 解決は一度だけ。useAuth の logout は毎レンダー新しい関数なので、この旗で閉じないと
    // 遷移待ちの再レンダーのたびにメニュー取得が走る。
    resolved.current = true;

    // 店舗コンソール資格が無い利用者（PLATFORM 権限のみの HQ 管理者など）は、
    // 授権店舗が空なのが正常。ここでセッションを捨てるとログアウト事故になるため平台側へ返す。
    if (!storeBridge) {
      router.replace('/platform/dashboard');
      return;
    }

    // 「行ける場所が無い」がサーバの答えとして確定した場合だけセッションを畳む。
    const deadEnd = () => {
      toast.error('アクセス可能な店舗がありません。管理者にお問い合わせください');
      void logout();
    };

    if (stores.length === 0) {
      deadEnd();
      return;
    }

    // 前回選択（switchStore が遷移前に書く）を最優先し、無ければ授権店舗の先頭。
    const lastUsed = getPlatformStoreId();
    const storeId = stores.some(store => String(store.id) === lastUsed)
      ? (lastUsed as string)
      : String(stores[0].id);

    const next = readNext();
    if (next) {
      setPlatformStore(storeId);
      router.replace(resolveStoreHref(next, storeId));
      return;
    }

    menuApi
      .getMenus()
      .then(menus => {
        const target = firstStorePath(menus);
        if (!target) {
          // 取得は成功していて、その上で行ける画面が 1 つも無い＝授権の答えが空。
          deadEnd();
          return;
        }
        setPlatformStore(storeId);
        router.replace(resolveStoreHref(target, storeId));
      })
      .catch(() => {
        // 取得そのものの失敗（ネットワーク・5xx）は授権の答えではないのでセッションを捨てない。
        // ここで畳むと、メニュー障害時にサイドバーが出す入口リンク自体がログアウトボタンになる。
        resolved.current = false;
        setFetchFailed(true);
      });
  }, [stores, storeBridge, router, logout, attempt]);

  if (fetchFailed) {
    return (
      <div className="mx-auto max-w-md space-y-3">
        <p className="text-sm text-foreground">メニューを取得できませんでした。</p>
        <Button
          onClick={() => {
            setFetchFailed(false);
            setAttempt(count => count + 1);
          }}
        >
          再試行
        </Button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <p className="text-sm text-muted-foreground">読み込み中...</p>
    </div>
  );
}
