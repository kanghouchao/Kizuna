'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { MenuProvider } from '@/entities/menu';
import { MeProvider, StoreContextProvider, useAuth, useStoreContext } from '@/entities/user';
import { isStorePath } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';

interface AdminShellProps {
  /** サイドバーとヘッダーは同じ層の別 widget なので、組み立てる app 層から差し込む。 */
  sidebar: React.ReactNode;
  header: React.ReactNode;
  children: React.ReactNode;
}

/**
 * 全従業員に共通の一つの管理画面シェル。/admin と /store はマウント接頭辞にすぎず、
 * 両者の共通祖先の layout がこれを一度だけ描く。店舗コンテキスト・本人情報・メニューはここで1つだけ搭載する。
 */
export function AdminShell({ sidebar, header, children }: AdminShellProps) {
  return (
    <StoreContextProvider>
      <MeProvider>
        <MenuProvider>
          {/* 管理コンソールは下限幅を持つ。これより狭い窓では横スクロールへ倒し、
          はみ出した操作を切り落とさない（切り落とすと到達手段が消える）。
          値はヘッダー全体の最悪所要幅 511px（見出し 90 + 操作行 357 + 左右余白 64。
          操作行は表示名が上限まで伸びた場合）を上回る位置に取る。 */}
          <div className="flex h-screen bg-background overflow-x-auto">
            {sidebar}

            <div className="flex-1 flex flex-col min-w-[44rem] overflow-hidden">
              {header}

              {/* relative: 絶対配置の子（Select が描画するフォーム互換用の隠し input 等）の
              包含ブロックをこのスクロール領域に閉じ込め、ページ全体が伸びるのを防ぐ */}
              <main className="relative flex-1 overflow-y-auto p-8">
                <div className="max-w-7xl mx-auto">
                  <StoreScopedContent>{children}</StoreScopedContent>
                </div>
              </main>
            </div>
          </div>
        </MenuProvider>
      </MeProvider>
    </StoreContextProvider>
  );
}

/**
 * 店舗の画面（/store/{storeId} 配下）は URL の店舗が授権店舗に含まれると確かめてから描く。授権外の店舗を
 * 名指す取得を飛ばさないため。店舗をキーにして、レールで別の店舗へ移ったら丸ごと描き直させる。
 */
function StoreScopedContent({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { logout } = useAuth();
  const { stores, currentStoreId, currentStore, loadFailed, reload } = useStoreContext();

  if (!isStorePath(pathname)) {
    return children;
  }

  if (stores === null) {
    return loadFailed ? (
      <RegionError message="店舗情報の取得に失敗しました" onRetry={reload} />
    ) : (
      <p className="text-sm text-muted-foreground">読み込み中...</p>
    );
  }

  if (stores.length === 0) {
    // 行き止まりは読まれてから終わる。自動でログアウトすると説明が読まれないまま消えるので、
    // セッションを畳むかは利用者がボタンで決める。
    return (
      <div role="alert" className="mx-auto max-w-md space-y-3">
        <p className="text-sm text-foreground">
          アクセス可能な店舗がありません。管理者にお問い合わせください
        </p>
        <Button type="button" variant="outline" onClick={() => logout()}>
          ログアウト
        </Button>
      </div>
    );
  }

  if (currentStore === undefined) {
    return (
      <div role="alert" className="mx-auto max-w-md space-y-3">
        <p className="text-sm text-foreground">この店舗の画面は開けません</p>
        <Button variant="outline" render={<Link href="/admin" />}>
          ダッシュボードへ
        </Button>
      </div>
    );
  }

  return <div key={currentStoreId}>{children}</div>;
}
