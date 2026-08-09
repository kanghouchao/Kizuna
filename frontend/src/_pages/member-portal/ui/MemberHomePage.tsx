'use client';

import Link from 'next/link';
import { QRCodeSVG } from 'qrcode.react';
import { memberApi } from '@/entities/member';
import { useAuth } from '@/entities/user';
import { useResource } from '@/shared/lib';
import { Button, Card, CardContent, CardHeader, CardTitle, RegionError } from '@/shared/ui';

/** 会員コードを 4 桁区切りで読みやすく整形する（例: 1234 5678 9012）。 */
function formatMemberCode(code: string): string {
  return code.replace(/(\d{4})(?=\d)/g, '$1 ');
}

/**
 * 会員ポータルのホーム。来店時に提示する会員コードを QR とテキストで表示する。
 * 店舗側がこのコードを読み取って顧客台帳と紐づける（自動マッチングは行わない既定）。
 */
export function MemberHomePage() {
  const { logout } = useAuth();
  const { data: home, isLoading, failure, reload } = useResource(() => memberApi.home());
  const displayName = home?.display_name ?? null;
  const memberCode = home?.member_code ?? null;

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">
        {displayName ? `${displayName} さん` : 'ホーム'}
      </h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            会員コード
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : failure !== null || memberCode === null ? (
            // 読めなかったカードに「再読み込みしてください」と書いても、それは頁ごと読み直せと
            // 言っているだけ。領域が自分で失敗を名乗り、自分の再試行を持つ。
            // `memberCode === null` は型の絞り込み — 会員コードは非 null 制約と生成時検証を
            // 通っており、中身の無い応答はフックが失敗へ倒すので、ここには失敗の姿として届く
            <RegionError message="会員コードを取得できませんでした" onRetry={() => void reload()} />
          ) : (
            <div className="flex flex-col items-center gap-4">
              <div className="rounded-xl border bg-card p-4">
                <QRCodeSVG value={memberCode} size={192} aria-label="会員コードQR" role="img" />
              </div>
              <p className="text-3xl font-bold tracking-wider text-foreground">
                {formatMemberCode(memberCode)}
              </p>
              <p className="text-sm text-muted-foreground">
                来店時にこのコードを提示すると、店舗があなたの利用をこのアカウントに紐づけます。
              </p>
            </div>
          )}
        </CardContent>
      </Card>
      <Button render={<Link href="/member/reservations/" />} className="mt-4 w-full">
        予約を見る
      </Button>
      <Button type="button" variant="outline" onClick={() => logout()} className="mt-6 w-full">
        ログアウト
      </Button>
    </div>
  );
}
