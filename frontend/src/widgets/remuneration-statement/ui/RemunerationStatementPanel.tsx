'use client';
import { useEffect, useState } from 'react';
import { remunerationApi, type StatementScope, type Day } from '../api';
import { hasPermission, readTokenClaims, useResource } from '@/shared/lib';
import {
  Button,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableCard,
} from '@/shared/ui';
import { Paging } from './Paging';
import { RemunerationManagement } from './RemunerationManagement';
const yen = (value: number | undefined) =>
  value === undefined ? '計算待ち' : `¥${value.toLocaleString('ja-JP')}`;
const labels: Record<Day['guarantee_status'], string> = {
  NOT_ELIGIBLE: '4時間未満・実績なし',
  PENDING_ATTENDANCE: '退勤記録待ち',
  NOT_CONFIGURED: '日額未設定',
  STOPPED: '保証停止',
  CALCULATED: '計算済み',
};
export function RemunerationStatementPanel({
  scope,
  month,
}: {
  scope: StatementScope;
  month: string;
}) {
  const [allowed, setAllowed] = useState(false);
  const [open, setOpen] = useState(false);
  useEffect(
    () =>
      setAllowed(scope.scope === 'self' || hasPermission(readTokenClaims(), 'REMUNERATION_VIEW')),
    [scope.scope]
  );
  if (!allowed) return null;
  return (
    <section className="mt-6 space-y-4" aria-label="保証とボーナス">
      <Button type="button" variant="outline" aria-expanded={open} onClick={() => setOpen(!open)}>
        保証・ボーナスを含む月次明細
      </Button>
      {open && <Statement key={JSON.stringify(scope) + month} scope={scope} month={month} />}
    </section>
  );
}
function Statement({ scope, month }: { scope: StatementScope; month: string }) {
  const [orderPage, setOrderPage] = useState(0),
    [bonusPage, setBonusPage] = useState(0),
    [revision, setRevision] = useState(0);
  const result = useResource(
    () => remunerationApi.statement(scope, month, orderPage, bonusPage),
    [
      scope.scope,
      'personId' in scope ? scope.personId : null,
      'storeId' in scope ? scope.storeId : null,
      month,
      orderPage,
      bonusPage,
      revision,
    ]
  );
  const data = !result.isLoading && result.failure === null ? result.data : null;
  return (
    <div className="min-w-0 space-y-6 rounded-lg border p-4 sm:p-6">
      <div className="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
        <h2 className="text-xl font-semibold">受注報酬・保証・ボーナス</h2>
        <Button type="button" variant="outline" onClick={() => setRevision(v => v + 1)}>
          内訳を再照会
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        保証は日ごとの不足分を合計します。出勤は記録済み区間を合算し、未記録の休憩は控除しません。支払済み額ではありません。PDF
        は受注報酬のみです。
      </p>
      {result.isLoading && <p role="status">月次内訳を読み込み中...</p>}
      {result.failure !== null && (
        <RegionError message="月次内訳を取得できませんでした。" onRetry={result.reload} />
      )}
      {data && (
        <>
          <p>
            {data.store_name} / {data.name} / {data.month}
          </p>
          <dl className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <dt>受注報酬</dt>
              <dd className="text-2xl font-semibold">{yen(data.order_total)}</dd>
            </div>
            <div>
              <dt>日額保証の不足分合計</dt>
              <dd className="text-2xl font-semibold">{yen(data.guarantee_total)}</dd>
            </div>
            <div>
              <dt>ボーナス付与</dt>
              <dd className="text-2xl font-semibold">{yen(data.bonus_total)}</dd>
            </div>
            <div>
              <dt>内訳の合計</dt>
              <dd className="text-3xl font-bold">{yen(data.total)}</dd>
            </div>
          </dl>
          {data.total === undefined && (
            <p role="status">
              日額未設定または退勤記録待ちの日があります。現在計算できる保証の小計は{' '}
              {yen(data.known_guarantee_total)} です。
            </p>
          )}
          <p className="text-sm text-muted-foreground">生成日時：{data.generated_at}</p>
          <TableCard>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>営業日</TableHead>
                  <TableHead>受注報酬</TableHead>
                  <TableHead>日額</TableHead>
                  <TableHead>出勤・条件</TableHead>
                  <TableHead>保証不足分</TableHead>
                  <TableHead>ボーナス</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.days.map(day => (
                  <TableRow key={day.business_date}>
                    <TableCell>{day.business_date}</TableCell>
                    <TableCell>{yen(day.order_amount)}</TableCell>
                    <TableCell>
                      {day.daily_amount === undefined ? '—' : yen(day.daily_amount)}
                    </TableCell>
                    <TableCell>
                      {labels[day.guarantee_status]}
                      <p>
                        終了済み区間：
                        {day.closed_duration
                          .replace(/^PT/, '')
                          .replace('H', '時間')
                          .replace('M', '分')
                          .replace('S', '秒')}
                      </p>
                      {day.attendance_incomplete && <p>未終了の記録あり</p>}
                    </TableCell>
                    <TableCell>{yen(day.guarantee_amount)}</TableCell>
                    <TableCell>{yen(day.bonus_amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableCard>
          <h3 className="font-semibold">この集計の受注根拠</h3>
          <TableCard>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>営業日</TableHead>
                  <TableHead>受注番号</TableHead>
                  <TableHead>サービス</TableHead>
                  <TableHead>有効報酬</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.orders.content.map(order => (
                  <TableRow key={order.order_id}>
                    <TableCell>{order.business_date}</TableCell>
                    <TableCell>{order.order_id}</TableCell>
                    <TableCell className="whitespace-normal">
                      {order.service_summary}
                      {order.completion_invalidated && '（無効化済み）'}
                    </TableCell>
                    <TableCell>{yen(order.accrued_remuneration)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableCard>
          <Paging
            page={orderPage}
            total={data.orders.total_pages}
            label="受注"
            onPage={setOrderPage}
          />
          <h3 className="font-semibold">ボーナスの根拠</h3>
          {data.bonus_awards.content.length === 0 ? (
            <p>この月のボーナスはありません。</p>
          ) : (
            <TableCard>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>帰属日</TableHead>
                    <TableHead>説明</TableHead>
                    <TableHead>有効額</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.bonus_awards.content.map(bonus => (
                    <TableRow key={bonus.id}>
                      <TableCell>{bonus.award_date}</TableCell>
                      <TableCell className="whitespace-normal">
                        {bonus.reason}
                        {bonus.cancelled && '（取消済み）'}
                      </TableCell>
                      <TableCell>{yen(bonus.effective_amount)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableCard>
          )}
          <Paging
            page={bonusPage}
            total={data.bonus_awards.total_pages}
            label="ボーナス"
            onPage={setBonusPage}
          />
        </>
      )}
      {scope.scope === 'store' && (
        <RemunerationManagement
          personId={scope.personId}
          personName={data?.name ?? `本人 ID ${scope.personId}`}
          month={month}
          refreshKey={revision}
          onSaved={() => setRevision(v => v + 1)}
        />
      )}
    </div>
  );
}
