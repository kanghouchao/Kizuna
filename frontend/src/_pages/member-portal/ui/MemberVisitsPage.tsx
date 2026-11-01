'use client';

import Link from 'next/link';
import { MemberVisit, memberVisitApi } from '@/entities/order';
import { useListPage } from '@/shared/lib';
import { Button, Card, CardContent, CardHeader, CardTitle, Pager, RegionError } from '@/shared/ui';

/** 来店 1 件。人数と担当は記録の無い来店があるため、無い場合はその項目だけ落として行は残す。 */
function VisitRow({ visit }: { visit: MemberVisit }) {
  return (
    <li className="rounded-[10px] border bg-card p-4 shadow-sm">
      <p className="flex items-center justify-between gap-2 text-sm font-medium text-foreground">
        <span>{visit.visited_on}</span>
        <span className="text-primary-strong">
          +{visit.granted_points.toLocaleString('ja-JP')} pt
        </span>
      </p>
      <p className="text-sm text-muted-foreground">{visit.store_name}</p>
      <p className="text-xs text-muted-foreground">
        {visit.pax != null && <span>{visit.pax} 名</span>}
        {visit.cast_name && <span className="ml-2">担当: {visit.cast_name}</span>}
      </p>
    </li>
  );
}

/**
 * 会員ポータルの来店履歴。全店舗の来店を新しい順に並べる。
 *
 * 出るのは会計の済んだ来店だけで、申請中の予約は「予約」の画面が別に追う。店舗との関連を解除しても
 * 過去の来店は残る（来店は確定した記録であって、現在の関連の写しではない）。
 */
export function MemberVisitsPage() {
  const list = useListPage<MemberVisit>(page => memberVisitApi.list(page));
  const { rows: visits, isLoading, failed } = list;

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">来店履歴</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            これまでの来店
          </CardTitle>
        </CardHeader>
        <CardContent>
          {failed ? (
            <RegionError
              message="来店履歴を取得できませんでした。"
              onRetry={() => void list.reload()}
            />
          ) : isLoading && visits.length === 0 ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : visits.length === 0 ? (
            <p className="text-sm text-muted-foreground">来店の記録はまだありません。</p>
          ) : (
            <ul className="space-y-3">
              {/* 来店は台帳内部の ID を持たない（応答に出さない）。1 ページの中で並べ替えも削除も
                  起きないため、ページ内の位置がそのまま識別子になる。 */}
              {visits.map((visit, index) => (
                <VisitRow key={index} visit={visit} />
              ))}
            </ul>
          )}
          <div className="mt-4">
            <Pager
              page={list.page}
              pageCount={list.pageCount}
              disabled={isLoading}
              onPageChange={page => void list.onPageChange(page)}
            />
          </div>
        </CardContent>
      </Card>
      <Button render={<Link href="/member/" />} variant="outline" className="mt-6 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
