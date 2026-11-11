'use client';

import { useState } from 'react';
import { orderApi, OrderCorrectionSnapshot, ORDER_FEE_LINE_KIND_LABELS } from '@/entities/order';
import { useStoreContext } from '@/entities/user';
import { storePath, useListPage } from '@/shared/lib';
import {
  Button,
  ChangeEntry,
  ChangeLog,
  ChangeTable,
  type ChangeRow,
  Dialog,
  DialogFrame,
  Pager,
  RegionError,
} from '@/shared/ui';

interface Props {
  orderId: string;
  /** 横断一覧（/admin/orders）から開くときの行の店舗。省略時は現在店舗で読む。 */
  storeId?: number;
}
const dateTime = (value: string) => new Date(value).toLocaleString('ja-JP');
const yen = (value: number) => `¥${value.toLocaleString()}`;

type FeeLine = OrderCorrectionSnapshot['fee_lines'][number];
type SpecialService = OrderCorrectionSnapshot['special_services'][number];

const adoptionLabels = {
  HISTORICAL_CORRECTION: '歴史版本による訂正',
  ACCEPTED_TERMS: '本人受諾条件',
  CURRENT_SETTING: '現在設定',
} as const;

function feeLine(line: FeeLine | undefined) {
  if (!line) return '—';
  const sign = line.kind === 'DISCOUNT' || line.kind === 'POINT_REDEMPTION' ? '−' : '';
  return [
    line.name,
    `費用 ${sign}${yen(line.amount)} / 報酬 ${yen(line.remuneration)}`,
    line.duration_minutes != null && `${line.duration_minutes} 分`,
    line.revision_id && `版本 ${line.revision_number}（${line.revision_id}）`,
    line.adoption_basis && `採用根拠 ${adoptionLabels[line.adoption_basis]}`,
    line.adopted_at && `採用日時 ${dateTime(line.adopted_at)}`,
    line.system_owned && 'システム専有明細',
  ]
    .filter(Boolean)
    .join('\n');
}

function specialService(item: SpecialService | undefined) {
  if (!item) return '—';
  return [
    item.name,
    `条件版本 ${item.terms_version} / 在籍 ${item.enrollment_id}`,
    item.consent_event_id
      ? `受諾記録 ${item.consent_event_id}（版本 ${item.consent_version}）`
      : '歴史版本による訂正',
  ].join('\n');
}

/**
 * 前後の一方にしかない明細・特殊サービスも行に出す。明細は訂正を跨いで line_id が保たれる。
 * 同じ id でも版の採り直しで名称が変わるため、名称は見出しでなく前後それぞれのセルに出す。
 */
function pairs<T>(before: T[], after: T[], id: (item: T) => string | undefined) {
  const keyed = (items: T[], side: string) =>
    new Map(items.map((item, i) => [id(item) ?? `${side}:${i}`, item]));
  const was = keyed(before, 'before');
  const now = keyed(after, 'after');
  return [...new Set([...was.keys(), ...now.keys()])].map(key => ({
    key,
    before: was.get(key),
    after: now.get(key),
  }));
}

function correctionRows(
  before: OrderCorrectionSnapshot,
  after: OrderCorrectionSnapshot
): ChangeRow[] {
  const plain = (label: string, read: (value: OrderCorrectionSnapshot) => string) => ({
    label,
    before: read(before),
    after: read(after),
  });
  return [
    plain('完了の扱い', v => (v.completion_invalidated ? '無効化済み' : '有効')),
    plain('請求', v => yen(v.total_fee)),
    plain('発生済み報酬', v => yen(v.accrued_remuneration)),
    plain('固定報酬', v => yen(v.total_remuneration)),
    plain('総時間', v => `${v.total_duration_minutes} 分`),
    plain('実際の到着', v => v.actual_arrival_time ?? '未記録'),
    plain('実際の終了', v => v.actual_end_time ?? '未記録'),
    plain(
      'コース',
      v => `${v.course.name} / 版本 ${v.course.revision_number} / ${v.course.duration_minutes} 分`
    ),
    ...pairs(before.fee_lines, after.fee_lines, line => line.line_id).map(pair => {
      const kind = (pair.after ?? pair.before)!.kind;
      return {
        key: `line:${pair.key}`,
        label: `明細：${ORDER_FEE_LINE_KIND_LABELS[kind]}`,
        before: feeLine(pair.before),
        after: feeLine(pair.after),
      };
    }),
    ...pairs(before.special_services, after.special_services, item => item.service_id).map(
      pair => ({
        key: `special:${pair.key}`,
        label: '特殊サービス',
        before: specialService(pair.before),
        after: specialService(pair.after),
      })
    ),
  ];
}

export function OrderCorrectionHistory({ orderId, storeId }: Props) {
  // /admin からも開かれるので useCurrentStoreId は使えない。店舗の画面でだけ URL の店舗が定まる。
  const { currentStoreId } = useStoreContext();
  const [status, setStatus] = useState<number>();
  const history = useListPage(page =>
    orderApi.correctionHistory(orderId, page, storeId).catch(error => {
      setStatus(error?.response?.status);
      throw error;
    })
  );
  if (history.failed)
    return (
      <RegionError
        message={
          status === 403
            ? '履歴を閲覧する権限がありません。'
            : status === 404
              ? '受注が見つからないか、閲覧範囲外です。'
              : '履歴を取得できませんでした。'
        }
        {...(status === 404
          ? {
              fallback: {
                href:
                  storeId === undefined && currentStoreId !== undefined
                    ? storePath(currentStoreId, '/orders')
                    : '/admin/orders',
                label: '一覧へ戻る',
              },
            }
          : { onRetry: () => void history.reload() })}
      />
    );
  return (
    <div className="space-y-6 text-sm">
      <p className="text-muted-foreground">
        提供内容の訂正履歴です。報酬は発生額であり、支払済み額ではありません。ポイント・会員帰属・実入出金はこの訂正で変わりません。
      </p>
      <ChangeLog>
        {history.rows.map(entry => (
          <ChangeEntry
            key={entry.correction_id}
            title={`${
              entry.change_type === 'COMPLETION_INVALIDATION' ? '誤完了の無効化日時' : '訂正日時'
            } ${dateTime(entry.corrected_at)} / 操作者 ${entry.corrected_by ?? '削除済み利用者'}`}
            meta={
              <>
                <p className="whitespace-pre-wrap text-foreground">{entry.reason}</p>
                <p>
                  原営業日 {entry.business_date} / 原完了日時 {dateTime(entry.completed_at)}
                </p>
                <p>
                  訂正 ID {entry.correction_id} / 受注版 {entry.before_version} →{' '}
                  {entry.after_version}
                </p>
                {entry.after.completion_invalidated && (
                  <p>無効化済み。明細の費用・報酬は原記録で、有効額は零です。</p>
                )}
              </>
            }
          >
            <ChangeTable rows={correctionRows(entry.before, entry.after)} />
          </ChangeEntry>
        ))}
      </ChangeLog>
      {history.isLoading && <p role="status">履歴を読み込み中...</p>}
      {!history.isLoading && history.rows.length === 0 && <p>訂正履歴はありません</p>}
      <Pager
        page={history.page}
        pageCount={history.pageCount}
        total={history.total}
        rowCount={history.rows.length}
        disabled={history.isLoading}
        onPageChange={page => void history.onPageChange(page)}
      />
    </div>
  );
}

export function OrderCorrectionHistoryDialog(props: Props) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
        訂正履歴
      </Button>
      <OrderCorrectionHistoryModal {...props} open={open} onOpenChange={setOpen} />
    </>
  );
}

export function OrderCorrectionHistoryModal({
  open,
  onOpenChange,
  ...props
}: Props & { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogFrame
        size="lg"
        title="訂正履歴"
        description={`受注 ${props.orderId} の費用・報酬の変更記録`}
      >
        <OrderCorrectionHistory key={`${props.storeId ?? 'current'}:${props.orderId}`} {...props} />
      </DialogFrame>
    </Dialog>
  );
}
