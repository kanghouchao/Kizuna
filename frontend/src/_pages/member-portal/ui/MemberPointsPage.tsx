'use client';

import Link from 'next/link';
import { MemberPointEntry, POINT_ENTRY_TYPE_LABELS, memberPointApi } from '@/entities/point';
import { useCursorList, useResource } from '@/shared/lib';
import { Badge, Button, Card, CardContent, CardHeader, CardTitle, RegionError } from '@/shared/ui';

/** 1 回に読み込む件数。会員の画面は縦に伸ばす前提なので、追加読み込みで過去へ遡る。 */
const PAGE_SIZE = 20;

/** 増減は符号を付けて出す。加算と減算が同じ一本に並ぶので、符号が無いとどちらか読めない。 */
function formatAmount(amount: number): string {
  return `${amount > 0 ? '+' : ''}${amount.toLocaleString('ja-JP')}`;
}

/** 明細 1 行。発生店舗と有効期限は持たない仕訳があるため、無い場合は行ごと畳まずその項目だけ落とす。 */
function EntryRow({ entry }: { entry: MemberPointEntry }) {
  return (
    <li className="rounded-[10px] border bg-card p-4 shadow-sm">
      <p className="flex items-center justify-between gap-2 text-sm font-medium text-foreground">
        <span className="flex items-center gap-2">
          {entry.entry_type && (
            <Badge variant="outline" className="border-transparent bg-muted text-foreground">
              {POINT_ENTRY_TYPE_LABELS[entry.entry_type]}
            </Badge>
          )}
          {entry.occurred_on}
        </span>
        <span className={entry.amount > 0 ? 'text-primary-strong' : 'text-foreground'}>
          {formatAmount(entry.amount)} pt
        </span>
      </p>
      <p className="text-sm text-muted-foreground">{entry.store_name ?? '店舗なし'}</p>
      {entry.expires_on && (
        <p className="text-xs text-muted-foreground">有効期限: {entry.expires_on}</p>
      )}
    </li>
  );
}

/**
 * 会員ポータルのポイント画面。現在残高と、全店舗・全種別の明細を新しい順に並べる。
 *
 * 残高と明細は別々に取る。片方が読めなくてももう片方は読めてよく、失敗はそれぞれの領域が自分で名乗る。
 * 残高は明細の増減の総和ではない — 有効期限切れは仕訳を積まずに残高だけを減らすため、足し上げても一致しない。
 */
export function MemberPointsPage() {
  const {
    data: balance,
    isLoading: balanceLoading,
    failure: balanceFailure,
    reload: reloadBalance,
  } = useResource(() => memberPointApi.balance());
  // 1 回の取得は常に PAGE_SIZE 件で、続きは位置（カーソル）を渡して 1 回ずつ継ぎ足す。要求サイズ自体を
  // 膨らませると、サーバ側の取得上限に当たった時点でそれ以降の明細へ到達できなくなる。
  const {
    rows: entries,
    isLoading: entriesLoading,
    failed: entriesFailed,
    hasMore,
    reload: reloadEntries,
    loadMore,
  } = useCursorList<MemberPointEntry>(cursor =>
    memberPointApi.entries({ cursor, size: PAGE_SIZE })
  );

  return (
    <div className="mx-auto w-full max-w-md p-4">
      <h1 className="mt-2 text-lg font-semibold text-foreground">ポイント</h1>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            現在の残高
          </CardTitle>
        </CardHeader>
        <CardContent>
          {balanceFailure !== null ? (
            <RegionError message="残高を取得できませんでした。" onRetry={() => void reloadBalance()} />
          ) : balanceLoading || balance === null ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : (
            <p className="text-3xl font-bold tracking-wider text-foreground">
              {balance.balance.toLocaleString('ja-JP')} pt
            </p>
          )}
        </CardContent>
      </Card>
      <Card className="mt-4">
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            明細
          </CardTitle>
        </CardHeader>
        <CardContent>
          {entriesFailed ? (
            <RegionError message="明細を取得できませんでした。" onRetry={reloadEntries} />
          ) : entriesLoading && entries.length === 0 ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : entries.length === 0 ? (
            <p className="text-sm text-muted-foreground">ポイントの増減はまだありません。</p>
          ) : (
            <ul className="space-y-3">
              {/* 明細は台帳内部の ID を持たない（応答に出さない）。行は末尾に継ぎ足されるだけで並べ替えも
                  削除も起きないため、位置がそのまま識別子になる。 */}
              {entries.map((entry, index) => (
                <EntryRow key={index} entry={entry} />
              ))}
            </ul>
          )}
          {hasMore && (
            <Button
              type="button"
              variant="outline"
              className="mt-4 w-full"
              onClick={loadMore}
              disabled={entriesLoading}
            >
              もっと見る
            </Button>
          )}
        </CardContent>
      </Card>
      <Button render={<Link href="/member/" />} variant="outline" className="mt-6 w-full">
        ホームへ戻る
      </Button>
    </div>
  );
}
