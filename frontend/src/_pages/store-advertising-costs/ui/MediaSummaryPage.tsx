import { useEffect } from 'react';
import {
  advertisingApi,
  categoryLabels,
  type MediaSummary,
  type MediaReport,
} from '../api/advertising';
import { useListPage } from '@/shared/lib';
import { fromSpringPage } from '@/shared/api';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { CostExport } from './CostExport';

export function MediaSummaryPage({
  store,
  month,
  canExport,
}: {
  store: string;
  month: string;
  canExport: boolean;
}) {
  const list = useListPage<MediaSummary, void, Omit<MediaReport, 'rows'>>(async page => {
    const { rows, ...metadata } = await advertisingApi.media(month, page);
    return { ...fromSpringPage(rows), metadata };
  }, undefined);
  useEffect(() => {
    const refresh = () => void list.reload();
    window.addEventListener('advertising-operation', refresh);
    return () => window.removeEventListener('advertising-operation', refresh);
  }, [list.reload]);
  const visible = !list.isLoading && !list.failed ? list.metadata : null;
  return (
    <div className="space-y-6">
      <ListPage
        title="媒体別集計"
        description={`${month} の登録記録。同じ区分・保存媒体名だけをまとめています。`}
        state={list}
        emptyMessage={
          visible && visible.entry_count > 0
            ? 'このページに該当する媒体はありません'
            : 'この月の広告費は登録されていません'
        }
        errorMessage="媒体別集計を取得できませんでした。"
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>区分</TableHead>
              <TableHead className="min-w-40">媒体</TableHead>
              <TableHead className="text-right">費用行数</TableHead>
              <TableHead className="text-right">登録額</TableHead>
              <TableHead className="text-right">入力済み人数の合計（重複未除外）</TableHead>
              <TableHead>入力状況</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(row => (
              <TableRow key={JSON.stringify([row.category, row.media_name])}>
                <TableCell>{categoryLabels[row.category]}</TableCell>
                <TableCell className="min-w-40 max-w-64 whitespace-normal break-all">
                  {row.media_name}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.entry_count.toLocaleString()}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.recorded_amount.toLocaleString()}円
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.recorded_inquiry_count_sum === null
                    ? '未入力'
                    : `${row.recorded_inquiry_count_sum.toLocaleString()}人`}
                </TableCell>
                <TableCell>
                  <p>
                    {row.inquiry_status === 'UNRECORDED'
                      ? '全行未入力'
                      : row.inquiry_status === 'PARTIAL'
                        ? '一部未入力'
                        : '全行入力済み'}
                  </p>
                  <p className="text-sm">
                    入力済み {row.recorded_inquiry_entry_count.toLocaleString()}行 ／ 未入力{' '}
                    {row.unrecorded_inquiry_entry_count.toLocaleString()}行
                  </p>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <section className="rounded-lg border p-4 space-y-2" aria-label="集計の意味">
        <p>
          人数は費用行に手入力された値の合計です。同じ人が複数行に含まれる可能性があり、実人数や反響の全量を示すものではありません。
        </p>
        <p>未入力は0人に置き換えません。営業広告と採用広告、異なる媒体の人数は合算しません。</p>
      </section>
      {visible && (
        <section className="rounded-lg border p-4 space-y-2" aria-label="媒体集計の月合計">
          <div className="flex flex-wrap gap-8">
            {visible.category_totals.map(total => (
              <p key={total.category}>
                {categoryLabels[total.category]}：
                <strong>{total.recorded_amount.toLocaleString()}円</strong>（
                {total.entry_count.toLocaleString()}行）
              </p>
            ))}
            <p>
              登録額合計：<strong>{visible.recorded_total_amount.toLocaleString()}円</strong>
            </p>
          </div>
          <p className="text-sm text-muted-foreground">
            全{visible.entry_count.toLocaleString()}
            行。登録なしは実際の費用がないことを意味しません。
          </p>
        </section>
      )}
      {canExport && <CostExport store={store} month={month} kind="media" />}
    </div>
  );
}
