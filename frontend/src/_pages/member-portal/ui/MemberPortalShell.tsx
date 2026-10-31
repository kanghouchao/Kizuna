'use client';

interface MemberPortalShellProps {
  children: React.ReactNode;
}

/**
 * 会員ポータルの共通シェル。モバイル優先の単画面構成で、桌面向け Sidebar shell とは別系統。
 * 本人確認はしない — 未ログインは各画面の最初の API の 401 が全域の 401 経路でログインへ差し戻し、
 * 戻り先（店舗つきの予約導線・申領画面のフラグメント）もそこで預ける。
 */
export function MemberPortalShell({ children }: MemberPortalShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  );
}
