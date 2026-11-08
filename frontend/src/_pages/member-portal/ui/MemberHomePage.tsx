'use client';

import { useId } from 'react';
import Link from 'next/link';
import { QRCodeSVG } from 'qrcode.react';
import { memberApi } from '@/entities/member';
import { type MemberOrderApplication, memberOrderApplicationApi } from '@/entities/order';
import { memberPointApi } from '@/entities/point';
import { formatDate, formatDateTime, tokyoDate, useResource } from '@/shared/lib';
import {
  Button,
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  RegionError,
  Skeleton,
  StatusBadge,
} from '@/shared/ui';

/** 会員コードを 4 桁区切りで読みやすく整形する（例: 1234 5678 9012）。 */
function formatMemberCode(code: string): string {
  return code.replace(/(\d{4})(?=\d)/g, '$1 ');
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="space-y-2">
      <h2 id={id} className="font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** 店舗側がこのコードを読み取って顧客台帳と紐づける（自動マッチングは行わない既定）。 */
function MemberCode() {
  const { data: home, failure, reload } = useResource(() => memberApi.home());
  const failed = (
    <RegionError message="会員コードを取得できませんでした" onRetry={() => void reload()} />
  );
  if (failure !== null) return failed;
  if (home === null) return <Skeleton role="status" aria-label="読み込み中" className="h-80" />;
  // 会員コードは非 null 制約と生成時検証を通っており、欠けた応答は読めなかったのと同じに扱う。
  if (home.member_code === undefined) return failed;
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border bg-card p-5 text-card-foreground">
      {home.display_name && <p className="font-semibold wrap-anywhere">{home.display_name} さん</p>}
      <div className="rounded-xl border bg-card p-4">
        <QRCodeSVG value={home.member_code} size={192} aria-label="会員コードQR" role="img" />
      </div>
      <p className="text-3xl font-bold tracking-wider tabular-nums">
        {formatMemberCode(home.member_code)}
      </p>
      <p className="text-center text-muted-foreground">
        来店時にこのコードを店舗へ提示してください。
      </p>
    </div>
  );
}

function PointBalance() {
  const { data, failure, reload } = useResource(() => memberPointApi.balance());
  if (failure !== null)
    return (
      <RegionError message="ポイント残高を取得できませんでした" onRetry={() => void reload()} />
    );
  if (data === null) return <Skeleton role="status" aria-label="読み込み中" className="h-20" />;
  return (
    <p className="rounded-xl border bg-card p-5 text-right text-card-foreground">
      <span className="text-4xl font-bold tabular-nums">
        {data.balance.toLocaleString('ja-JP')}
      </span>
      <span className="ml-1 font-semibold">pt</span>
    </p>
  );
}

type Upcoming = MemberOrderApplication & {
  business_date: string;
  status: 'PENDING' | 'CONFIRMED';
};

/**
 * 今日以降で最も近い申請中・承認の申請。サーバの失効は申請中にしか付かないため、承認済みの過去日は暦日で落とす。
 * 日付変更時刻より前の深夜は前日の予約が先に消える（境界を知るのはサーバだけ）。
 */
function pickNext(rows: MemberOrderApplication[], today: string): Upcoming | null {
  const upcoming = rows.filter(
    (row): row is Upcoming =>
      (row.status === 'PENDING' || row.status === 'CONFIRMED') &&
      !row.expired &&
      row.business_date !== undefined &&
      row.business_date >= today
  );
  const key = (row: Upcoming) => `${row.business_date}T${row.arrival_scheduled_start_time ?? ''}`;
  return upcoming.reduce<Upcoming | null>(
    (next, row) => (next === null || key(row) < key(next) ? row : next),
    null
  );
}

/** 一覧は希望日の新しい順なので、今日より前の日付が出たページで読むのを止める。普通は先頭ページだけで済む。 */
async function listFromToday(today: string): Promise<MemberOrderApplication[]> {
  const rows: MemberOrderApplication[] = [];
  for (let page = 0; ; page++) {
    const result = await memberOrderApplicationApi.list(page);
    rows.push(...result.rows);
    const last = result.rows.at(-1)?.business_date;
    if (last === undefined || last < today || page + 1 >= result.pageCount) return rows;
  }
}

const STATUS_WORD = { PENDING: '申請中', CONFIRMED: '承認' } as const;

function NextReservation() {
  const today = tokyoDate(new Date());
  const { data, failure, reload } = useResource(() => listFromToday(today));
  if (failure !== null)
    return <RegionError message="次の予約を取得できませんでした" onRetry={() => void reload()} />;
  if (data === null) return <ListRowsSkeleton rows={1} />;
  const next = pickNext(data, today);
  if (next === null)
    return (
      <EmptyState
        message="まだ予約はありません"
        action={
          <Button render={<Link href="/member/reservations/new/" />} className="h-12 w-full">
            新しく予約
          </Button>
        }
      />
    );
  const when = next.arrival_scheduled_start_time
    ? formatDateTime(`${next.business_date}T${next.arrival_scheduled_start_time}`, today)
    : formatDate(next.business_date, today);
  return (
    <ListRows label="次の予約">
      <ListRow
        title={<span className="font-semibold tabular-nums">{when}</span>}
        meta={next.store_name}
        trailing={<StatusBadge status={STATUS_WORD[next.status]} />}
        href="/member/reservations/"
      />
    </ListRows>
  );
}

/**
 * 会員のホーム。上から 会員コード → ポイント残高 → 次の予約（DESIGN.md「本人ポータル」）。
 * 自社の告知の区画は情報源ができるまで描画しない。
 */
export function MemberHomePage() {
  return (
    <div className="space-y-6">
      <Section title="会員コード">
        <MemberCode />
      </Section>
      <Section title="ポイント残高">
        <PointBalance />
      </Section>
      <Section title="次の予約">
        <NextReservation />
      </Section>
    </div>
  );
}
