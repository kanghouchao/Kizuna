'use client';

import { customerApi } from '@/entities/customer';
import { useCursorList } from '@/shared/lib';
import {
  Badge,
  Button,
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

interface MergeHistorySectionProps {
  customerId: string;
}

/**
 * 顧客編集ページの統合履歴区画。統合に取消は無く、誤統合の修復は「誰が・いつ・どの行を
 * どの行へ・何を移したか」を根拠とする人手作業である（ADR 0010）。この区画はその根拠を
 * 出すためだけにあり、統合を取り消す導線は持たない。
 */
export function MergeHistorySection({ customerId }: MergeHistorySectionProps) {
  const { rows, isLoading, failed, hasMore, reload, loadMore } = useCursorList(cursor =>
    customerApi.mergeHistory(customerId, { cursor })
  );

  return (
    <TableCard>
      <div className="border-b bg-muted/50 px-6 py-4">
        <h2 className="text-lg font-medium text-foreground">統合履歴</h2>
      </div>

      {isLoading ? (
        <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
      ) : failed ? (
        // 読めなかった履歴を残すと「統合履歴がありません」に化ける。区画自身が失敗を名乗る
        <RegionError
          message="統合履歴の取得に失敗しました"
          onRetry={() => reload()}
          className="justify-center p-8"
        />
      ) : rows.length === 0 ? (
        <div className="p-8 text-center text-muted-foreground">統合履歴がありません</div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>向き</TableHead>
              <TableHead>相手の顧客</TableHead>
              <TableHead>実行者・日時</TableHead>
              <TableHead>移した受注</TableHead>
              <TableHead>移した関連</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map(row => (
              <TableRow key={row.id}>
                <TableCell>
                  {row.direction === 'SURVIVING' ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      存続行として受けた
                    </Badge>
                  ) : (
                    <Badge variant="outline">被統合となった</Badge>
                  )}
                </TableCell>
                <TableCell className="text-foreground">
                  {/* 名前だけでは同名の別行と見分けが付かないので、根拠になる ID も並べて出す */}
                  <div>{row.counterpart_customer_name || '名称なし'}</div>
                  <div className="text-xs text-muted-foreground">{row.counterpart_customer_id}</div>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {/* 実行者は削除で欠けうるが、そのときも行は消さない */}
                  {`${row.merged_by_name || '不明'}・${new Date(row.merged_at).toLocaleString('ja-JP')}`}
                </TableCell>
                <TableCell className="text-muted-foreground">{row.moved_order_count} 件</TableCell>
                <TableCell className="text-muted-foreground">{row.moved_link_count} 件</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {hasMore && (
        <div className="flex justify-center border-t p-4">
          <Button variant="outline" onClick={() => loadMore()} disabled={isLoading}>
            さらに読み込む
          </Button>
        </div>
      )}
    </TableCard>
  );
}
