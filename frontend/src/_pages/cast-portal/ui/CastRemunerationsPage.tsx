'use client';

import { useState } from 'react';
import { selfRemunerationApi, SelfRemunerationEnrollment } from '@/entities/order';
import { formatDate, tokyoDate, useListPage, useResource } from '@/shared/lib';
import {
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  Money,
  Pager,
  RegionError,
  Skeleton,
  StoreChips,
} from '@/shared/ui';
import { remunerationApi, type Statement } from '@/widgets/remuneration-statement';
import { Failure, PANEL, remunerationStatus, shownRemuneration } from './RemunerationDetail';

// 在籍の数は本人の在籍・退店の回数で、ページを辿り切っても小さい
async function allEnrollments(): Promise<SelfRemunerationEnrollment[]> {
  const first = await selfRemunerationApi.enrollments(0);
  const rest = await Promise.all(
    Array.from({ length: Math.max(first.pageCount - 1, 0) }, (_, i) =>
      selfRemunerationApi.enrollments(i + 1)
    )
  );
  return [first, ...rest].flatMap(result => result.rows);
}

/** 同じ店舗に在籍し直したときだけ、在籍の期間を添えてチップを見分ける。 */
function chipName(
  enrollment: SelfRemunerationEnrollment,
  all: SelfRemunerationEnrollment[],
  today: string
) {
  const repeated = all.filter(other => other.store_id === enrollment.store_id).length > 1;
  if (!repeated) return enrollment.store_name;
  const period = enrollment.ended_at
    ? `${formatDate(tokyoDate(new Date(enrollment.ended_at)), today)}まで`
    : '在籍中';
  return `${enrollment.store_name}（${period}）`;
}

export function CastRemunerationsPage() {
  const [error, setError] = useState<unknown>();
  const enrollments = useResource(async () => {
    try {
      return await allEnrollments();
    } catch (failure) {
      setError(failure);
      throw failure;
    }
  });
  const [selected, setSelected] = useState<string | null>(null);
  const today = tokyoDate(new Date());

  if (enrollments.isLoading)
    return (
      <div className="space-y-4">
        <Skeleton className="h-28 w-full rounded-xl" />
        <ListRowsSkeleton />
      </div>
    );
  if (enrollments.failure)
    return <Failure error={error} onRetry={() => void enrollments.reload()} />;
  const rows = enrollments.data ?? [];
  if (rows.length === 0) return <EmptyState message="まだ報酬明細はありません" />;
  const current = rows.find(row => row.enrollment_id === selected) ?? rows[0];
  return (
    <div className="space-y-4">
      <StoreChips
        stores={rows.map(row => ({ id: row.enrollment_id, name: chipName(row, rows, today) }))}
        value={current.enrollment_id}
        onValueChange={setSelected}
      />
      <EnrollmentRemunerations key={current.enrollment_id} enrollment={current} today={today} />
    </div>
  );
}

/** 保証の計算待ちの日があると total は無い。そのときは計算できる分までを足して出す。 */
function shownTotal(statement: Statement): number {
  return (
    statement.total ??
    statement.order_total + statement.known_guarantee_total + statement.bonus_total
  );
}

function MonthTotal({ storeId, today }: { storeId: number; today: string }) {
  const total = useResource(() =>
    remunerationApi.statement({ scope: 'self', storeId }, today.slice(0, 7), 0, 0)
  );
  return (
    <section aria-labelledby="month-total" className={PANEL}>
      <h2 id="month-total" className="text-sm text-muted-foreground">
        今月の累計
      </h2>
      {total.isLoading ? (
        <Skeleton className="h-9 w-40" />
      ) : total.failure ? (
        <RegionError message="今月の累計の取得に失敗しました" onRetry={() => void total.reload()} />
      ) : (
        total.data && (
          <>
            <Money amount={shownTotal(total.data)} className="block text-3xl font-bold" />
            {total.data.total === undefined && (
              <p className="text-sm">
                日額未設定または退勤記録待ちの日があります。計算できる分までの合計です。
              </p>
            )}
          </>
        )
      )}
      <p className="text-sm text-muted-foreground">
        受注報酬・日額保証の不足分・ボーナスの合計です。支払済み額ではありません。
      </p>
    </section>
  );
}

function EnrollmentRemunerations({
  enrollment,
  today,
}: {
  enrollment: SelfRemunerationEnrollment;
  today: string;
}) {
  const list = useListPage(page => selfRemunerationApi.list(page, enrollment.enrollment_id));
  return (
    <>
      <MonthTotal storeId={enrollment.store_id} today={today} />
      <section aria-labelledby="remuneration-list" className="space-y-3">
        <h2 id="remuneration-list" className="font-semibold">
          明細
        </h2>
        {list.failed ? (
          <Failure error={list.error} onRetry={() => void list.reload()} />
        ) : list.isLoading ? (
          <ListRowsSkeleton />
        ) : list.rows.length === 0 ? (
          <EmptyState message="まだ報酬明細はありません" />
        ) : (
          <ListRows label="報酬明細">
            {list.rows.map(row => (
              <ListRow
                key={row.order_id}
                href={`/cast/remunerations/${encodeURIComponent(row.order_id)}`}
                title={formatDate(row.business_date, today)}
                meta={row.store_name}
                // 状態は金額の下に置く。店舗名の後ろに続けると、長い店舗名で語の途中から折り返す
                trailing={
                  <>
                    <Money amount={shownRemuneration(row)} className="block font-semibold" />
                    <span className="text-sm text-muted-foreground group-hover:text-foreground">
                      {remunerationStatus(row)}
                    </span>
                  </>
                }
              />
            ))}
          </ListRows>
        )}
        <Pager
          page={list.page}
          pageCount={list.pageCount}
          total={list.total}
          rowCount={list.rows.length}
          disabled={list.isLoading}
          onPageChange={page => void list.onPageChange(page)}
        />
      </section>
    </>
  );
}
