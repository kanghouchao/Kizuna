'use client';

import Link from 'next/link';
import { useState } from 'react';
import { notify } from '@/shared/notify';
import { MemberOrderApplication, memberOrderApplicationApi } from '@/entities/order';
import {
  formatDate,
  formatDateTime,
  getApiErrorMessage,
  tokyoDate,
  useListPage,
} from '@/shared/lib';
import {
  Button,
  ConfirmDialog,
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  Pager,
  RegionError,
  StatusBadge,
  type StatusWord,
} from '@/shared/ui';

const STATUS_WORDS: Record<NonNullable<MemberOrderApplication['status']>, StatusWord> = {
  PENDING: '申請中',
  CONFIRMED: '承認',
  DECLINED: '却下',
  WITHDRAWN: '取り下げ',
};

/**
 * 行の状態は PENDING のままでも、希望日を過ぎた申請はサーバの導出（expired）に従って失効と出す
 * — 画面が日時を比べると、営業日の区切り（日付変更時刻）とずれる。
 */
function statusWord(application: MemberOrderApplication): StatusWord | null {
  if (!application.status) return null;
  if (application.status === 'PENDING' && application.expired) return '失効';
  return STATUS_WORDS[application.status];
}

function when(application: MemberOrderApplication, today: string): string {
  if (!application.business_date) return '';
  return application.arrival_scheduled_start_time
    ? formatDateTime(
        `${application.business_date}T${application.arrival_scheduled_start_time}`,
        today
      )
    : formatDate(application.business_date, today);
}

function NewReservationButton() {
  return (
    <Button className="min-h-12 w-full text-lg" render={<Link href="/member/reservations/new" />}>
      新しく予約
    </Button>
  );
}

/** 会員ポータルの予約申請一覧。全店舗を集約し、未処理のものは本人が確認のうえ取り下げられる。 */
export function MemberReservationsPage() {
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [withdrawTarget, setWithdrawTarget] = useState<MemberOrderApplication | null>(null);
  const list = useListPage<MemberOrderApplication>(page => memberOrderApplicationApi.list(page));
  const { rows: reservations, isLoading: loading, failed } = list;
  const today = tokyoDate(new Date());
  const empty = !failed && !loading && reservations.length === 0;

  const withdraw = async (reservation: MemberOrderApplication) => {
    setProcessingId(reservation.id ?? null);
    try {
      await memberOrderApplicationApi.withdraw(reservation.id);
      notify.success('予約を取り下げました');
      await list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '取り下げに失敗しました'));
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <div className="space-y-4">
      {/* 空のときは空の表示が同じ入口を持つので、上部には重ねない */}
      {!empty && <NewReservationButton />}
      {failed ? (
        <RegionError message="予約を取得できませんでした。" onRetry={() => void list.reload()} />
      ) : loading && reservations.length === 0 ? (
        <ListRowsSkeleton />
      ) : empty ? (
        <EmptyState message="まだ予約はありません" action={<NewReservationButton />} />
      ) : (
        <ListRows label="予約">
          {reservations.map(reservation => {
            const status = statusWord(reservation);
            return (
              <ListRow
                key={reservation.id}
                title={<span className="font-medium">{reservation.store_name}</span>}
                meta={
                  <>
                    <span className="block tabular-nums">
                      {when(reservation, today)}
                      {reservation.pax ? ` · ${reservation.pax}名` : ''}
                    </span>
                    <span className="block">
                      {reservation.cast_name ? `指名: ${reservation.cast_name}` : '指名なし'}
                    </span>
                    {/* 右の列に置くと長い店舗名が細く折り返されるので、詳細の下に全幅で置く */}
                    {reservation.status === 'PENDING' && !reservation.expired && (
                      <Button
                        type="button"
                        variant="outline"
                        className="mt-2 min-h-12 w-full text-lg text-foreground"
                        onClick={() => setWithdrawTarget(reservation)}
                        // 結果が返るまで同じ行を押せると、済んだ取り下げをもう一度投げてしまう
                        disabled={loading || processingId === reservation.id}
                      >
                        取り下げる
                      </Button>
                    )}
                  </>
                }
                trailing={status && <StatusBadge status={status} />}
              />
            );
          })}
        </ListRows>
      )}
      <Pager
        page={list.page}
        pageCount={list.pageCount}
        total={list.total}
        rowCount={list.rows.length}
        disabled={loading}
        onPageChange={page => void list.onPageChange(page)}
      />
      <ConfirmDialog
        open={withdrawTarget !== null}
        title="予約を取り下げますか"
        description={
          withdrawTarget
            ? `${[withdrawTarget.store_name, when(withdrawTarget, today)].filter(Boolean).join(' ')} の予約を取り下げます。`
            : undefined
        }
        confirmLabel="取り下げる"
        cancelLabel="やめる"
        size="large"
        onConfirm={() => {
          if (withdrawTarget) void withdraw(withdrawTarget);
        }}
        onClose={() => setWithdrawTarget(null)}
      />
    </div>
  );
}
