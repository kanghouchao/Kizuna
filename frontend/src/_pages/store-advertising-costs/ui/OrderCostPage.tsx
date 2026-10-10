import { useEffect } from 'react';
import { advertisingApi, type OrderCostSummary, type OrderCostReport } from '../api/advertising';
import { useListPage } from '@/shared/lib';
import { fromSpringPage } from '@/shared/api';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { CostExport } from './CostExport';

const statusLabels: Record<OrderCostSummary['calculation_status'], string> = {
  CALCULATED: '記録値から算出',
  NO_COST_RECORDS: '営業広告費が未登録',
  NO_VALID_ORDERS: '有効完了受注が0件',
};
function yen(amount: number | null) {
  return amount === null ? '未登録' : `${amount.toLocaleString()}円`;
}
function ratio(value: string | null) {
  if (value === null) return '未算出';
  const [integer, fraction] = value.split('.');
  return `約 ${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}円`;
}

export function OrderCostPage({
  store,
  month,
  canExport,
}: {
  store: string;
  month: string;
  canExport: boolean;
}) {
  const list = useListPage<OrderCostSummary, void, Omit<OrderCostReport, 'rows'>>(async page => {
    const { rows, ...metadata } = await advertisingApi.orderCosts(month, page);
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
        title="受注あたり記録広告費"
        description={`${month} の登録営業広告費と、同じ保存媒体名の有効完了受注を比較します。0円の受注も1件と数えます。`}
        state={list}
        emptyMessage={
          visible && list.total > 0
            ? 'このページに該当する媒体はありません'
            : 'この月に比較対象の媒体記録はありません'
        }
        errorMessage="受注あたり記録広告費を取得できませんでした。"
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="min-w-40">媒体</TableHead>
              <TableHead className="text-right">登録営業広告費</TableHead>
              <TableHead className="text-right">有効完了受注</TableHead>
              <TableHead className="text-right">一件あたり記録費用</TableHead>
              <TableHead>算出状況</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(row => (
              <TableRow key={row.media_name}>
                <TableCell className="min-w-40 max-w-64 whitespace-normal break-all">
                  <span className="whitespace-pre-wrap">{row.media_name}</span>
                  {row.media_name !== row.media_name.trim() && (
                    <p className="text-sm">前後に空白あり</p>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {yen(row.recorded_sales_amount)}
                  <p className="text-sm">費用 {row.cost_entry_count.toLocaleString()}行</p>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {row.valid_completed_order_count.toLocaleString()}件
                  <p className="text-sm">
                    うち0円 {row.zero_amount_order_count.toLocaleString()}件
                  </p>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {ratio(row.cost_per_order)}
                </TableCell>
                <TableCell>{statusLabels[row.calculation_status]}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      {visible && (
        <section className="rounded-lg border p-4 space-y-2" aria-label="受注比較の月合計">
          <div className="flex flex-wrap gap-8">
            <p>
              登録営業広告費：<strong>{yen(visible.recorded_sales_amount)}</strong>（
              {visible.cost_entry_count.toLocaleString()}行）
            </p>
            <p>
              有効完了受注：
              <strong>
                {visible.valid_completed_order_count.toLocaleString()}件
              </strong>（うち0円 {visible.zero_amount_order_count.toLocaleString()}件）
            </p>
          </div>
          <p>
            媒体未入力の有効完了受注：
            <strong>{visible.unnamed_media_order_count.toLocaleString()}件</strong>
            。上記注文件数の内数で、媒体への割当てと単価算出から除外しています。
          </p>
        </section>
      )}
      <section className="rounded-lg border p-4 space-y-2" aria-label="記録費用の算出条件">
        <p>
          原営業日の自然月で集計します。取消・未完了・無効化は除外し、再提供は別の有効受注として数えます。採用広告費は含みません。
        </p>
        <p>
          保存媒体名は空白・大小文字・全半角の違いを含めて完全一致だけで比較します。別名を統合せず、媒体未入力を推測で割り当てません。
        </p>
        <p>
          費用未登録と0円登録は別です。費用未登録または有効完了受注が0件なら単価は未算出です。単価は登録額を件数で割り、小数第3位を四捨五入しています。
        </p>
        <p>
          同月同名の記録比較であり、実際の獲得費用・広告による因果・利益・ROIを示しません。未登録は実費0の証明ではありません。
        </p>
        <p>
          新客単価は未提供です。顧客未設定や過去記録の不足があり、顧客作成や最古の現存受注を初回とみなしていません。
        </p>
      </section>
      {canExport && <CostExport store={store} month={month} kind="orders" />}
    </div>
  );
}
