'use client';

import Link from 'next/link';
import { useState } from 'react';
import { formatMoney } from '@/shared/lib';
import { Badge, Card, CardContent, CardHeader, CardTitle, Skeleton } from '@/shared/ui';
import { PrototypeProvider, useProto, type Preset, type RegionDef, type Variant } from './controls';
import {
  MONTH_LABEL,
  PREV_MONTH_LABEL,
  TODAY_LABEL,
  fakeStores,
  groupMembers,
  groupMonthAdvertising,
  groupMonthRemuneration,
  groupRecentAudit,
  groupRecentMemberNotifications,
  groupRecentTasks,
  groupTotals,
} from './fake-data';
import {
  AsOf,
  Delta,
  NumLink,
  RankRow,
  RecentList,
  RegionBody,
  Stat,
  StatSkeleton,
  StoreStatusBadge,
  useVisible,
} from './parts';
import { NothingToShow } from './StoreDashboardPrototype';

// 試作: グループのダッシュボードの 3 案。?variant=A|B|C で切り替える。
const VARIANTS: Variant[] = [
  { key: 'A', name: '数字の段＋上位 10 の順位表' },
  { key: 'B', name: '順位が主・右に数字の列' },
  { key: 'C', name: '一枚の帳票＋棒入りの全店順位' },
];

const SALES = ['OPERATIONAL_REPORT_SET_VIEW'];
const REGIONS: RegionDef[] = [
  { key: 'groupToday', label: '今日の売上・件数', perms: SALES },
  { key: 'groupMonth', label: '今月の売上・件数', perms: SALES },
  { key: 'ranking', label: '店舗別の順位', perms: SALES },
  { key: 'storeCount', label: '担当店舗数', perms: [] },
  {
    key: 'remuneration',
    label: '今月の報酬',
    perms: [...SALES, 'REMUNERATION_SET_VIEW'],
    candidate: true,
  },
  {
    key: 'advertising',
    label: '今月の広告費',
    perms: [...SALES, 'ADVERTISING_COST_SET_VIEW'],
    candidate: true,
  },
  { key: 'members', label: '会員数', perms: ['MEMBER_VIEW'], candidate: true },
  {
    key: 'memberNotifications',
    label: '会員宛の通知',
    perms: ['MEMBER_NOTIFICATION_VIEW'],
    candidate: true,
  },
  { key: 'tasks', label: '定期処理', perms: ['TASK_MANAGE'], candidate: true },
  { key: 'audit', label: '監査', perms: ['AUDIT_VIEW'], candidate: true },
];

// 統括は今の種子の権限のグループの木の分。ほかはカスタムロールの例。
const PRESETS: Preset[] = [
  {
    label: '統括（既定）',
    perms: [
      'OPERATIONAL_REPORT_SET_VIEW',
      'REMUNERATION_SET_VIEW',
      'ADVERTISING_COST_SET_VIEW',
      'MEMBER_VIEW',
      'MEMBER_NOTIFICATION_VIEW',
      'TASK_MANAGE',
      'AUDIT_VIEW',
    ],
  },
  {
    label: '例: 経理',
    perms: ['OPERATIONAL_REPORT_SET_VIEW', 'REMUNERATION_SET_VIEW', 'ADVERTISING_COST_SET_VIEW'],
  },
  { label: '例: 会員担当', perms: ['MEMBER_VIEW', 'MEMBER_NOTIFICATION_VIEW'] },
  { label: '例: システム担当', perms: ['TASK_MANAGE', 'AUDIT_VIEW'] },
];

export function GroupDashboardPrototype({ variant }: { variant: string }) {
  return (
    <PrototypeProvider
      regions={REGIONS}
      presets={PRESETS}
      variants={VARIANTS}
      current={variant}
      showStoreOptions="group"
    >
      {variant === 'A' ? <VariantA /> : variant === 'B' ? <VariantB /> : <VariantC />}
    </PrototypeProvider>
  );
}

function useData() {
  const { storeCount, longNames, monthDown } = useProto();
  const stores = fakeStores(storeCount, longNames);
  const totals = groupTotals(stores);
  const prevMonth = monthDown
    ? Math.round(totals.monthSales * 1.12)
    : Math.round(totals.monthSales * 0.91);
  return { stores, totals, prevMonth };
}

function Heading() {
  return (
    <div className="space-y-1">
      <h1 className="text-2xl font-bold text-foreground">グループ</h1>
      <AsOf>{TODAY_LABEL}</AsOf>
    </div>
  );
}

function TodayStat() {
  const { totals } = useData();
  return (
    <RegionBody
      region="groupToday"
      failMessage="今日の売上の取得に失敗しました"
      skeleton={<StatSkeleton />}
    >
      <Stat
        note="今日の売上（担当店舗の合計）"
        value={formatMoney(totals.todaySales)}
        sub={`完了 ${totals.todayCount} 件`}
      />
    </RegionBody>
  );
}

function MonthStat() {
  const { totals, prevMonth } = useData();
  return (
    <RegionBody
      region="groupMonth"
      failMessage="今月の売上の取得に失敗しました"
      skeleton={<StatSkeleton />}
    >
      <Stat
        note={`今月の売上（${MONTH_LABEL}）`}
        value={formatMoney(totals.monthSales)}
        sub={`完了 ${totals.monthCount.toLocaleString()} 件`}
        delta={<Delta now={totals.monthSales} before={prevMonth} label={PREV_MONTH_LABEL} />}
      />
    </RegionBody>
  );
}

function StoreCountStat() {
  const { totals, stores } = useData();
  return (
    <RegionBody
      region="storeCount"
      failMessage="担当店舗の取得に失敗しました"
      skeleton={<StatSkeleton />}
    >
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">担当店舗</p>
        <p className="text-3xl font-bold tabular-nums text-foreground">
          <NumLink>{stores.length} 店舗</NumLink>
        </p>
        <p className="flex flex-wrap gap-2 text-sm">
          <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
            稼働中 {totals.active}
          </Badge>
          <Badge variant="outline">準備中 {totals.preparing}</Badge>
        </p>
      </div>
    </RegionBody>
  );
}

function RankSkeleton({ rows }: { rows: number }) {
  return (
    <div>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3 px-6 py-3">
          <Skeleton className="size-8 rounded-full" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-20" />
        </div>
      ))}
    </div>
  );
}

// ---- 案 A: 上段に数字のカード 3 枚、下に上位 10 の順位表（残りは「すべて見る」）。

function VariantA() {
  const visible = useVisible();
  const { stores } = useData();
  const [all, setAll] = useState(false);
  const shown = all ? stores : stores.slice(0, 10);
  const any = REGIONS.some(r => visible(r.key));
  return (
    <div className="space-y-6">
      <Heading />
      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        {visible('groupToday') && (
          <Card>
            <CardContent>
              <TodayStat />
            </CardContent>
          </Card>
        )}
        {visible('groupMonth') && (
          <Card>
            <CardContent>
              <MonthStat />
            </CardContent>
          </Card>
        )}
        {visible('storeCount') && (
          <Card>
            <CardContent>
              <StoreCountStat />
            </CardContent>
          </Card>
        )}
      </div>
      {visible('ranking') && (
        <Card className="gap-0 py-0">
          <CardHeader className="py-4">
            <CardTitle role="heading" aria-level={2}>
              店舗別の今月の売上
            </CardTitle>
          </CardHeader>
          <RegionBody
            region="ranking"
            failMessage="店舗別の売上の取得に失敗しました"
            skeleton={<RankSkeleton rows={5} />}
          >
            <div className="divide-y border-t">
              {shown.map((s, i) => (
                <RankRow key={s.id} rank={i + 1} name={s.name} amount={s.monthSales} />
              ))}
            </div>
            {stores.length > 10 && (
              <button
                type="button"
                className="w-full border-t px-6 py-3 text-left text-sm text-primary-strong hover:bg-muted"
                onClick={() => setAll(a => !a)}
              >
                {all ? '上位 10 店舗だけ表示' : `すべての店舗（${stores.length}）を表示`}
              </button>
            )}
          </RegionBody>
        </Card>
      )}
      {!any && <NothingToShow />}
    </div>
  );
}

// ---- 案 B: 左に全店の順位（枠の中で縦スクロール）、右の細い列に数字を縦積み。

function VariantB() {
  const visible = useVisible();
  const { stores } = useData();
  const statsAny = ['groupToday', 'groupMonth', 'storeCount'].some(visible);
  const any = statsAny || visible('ranking');
  return (
    <div className="space-y-6">
      <Heading />
      <div className="@container">
        <div
          className={
            statsAny && visible('ranking')
              ? 'grid grid-cols-1 gap-6 @4xl:grid-cols-[minmax(0,1fr)_20rem]'
              : 'grid grid-cols-1 gap-6'
          }
        >
          {visible('ranking') && (
            <Card className="order-2 gap-0 py-0 @4xl:order-1">
              <CardHeader className="py-4">
                <CardTitle role="heading" aria-level={2}>
                  店舗別の今月の売上（{stores.length} 店舗）
                </CardTitle>
              </CardHeader>
              <RegionBody
                region="ranking"
                failMessage="店舗別の売上の取得に失敗しました"
                skeleton={<RankSkeleton rows={8} />}
              >
                <div className="max-h-[32rem] divide-y overflow-y-auto border-t">
                  {stores.map((s, i) => (
                    <RankRow
                      key={s.id}
                      rank={i + 1}
                      name={s.name}
                      amount={s.monthSales}
                      extra={
                        <span className="block text-xs text-muted-foreground tabular-nums">
                          {s.monthCount} 件
                        </span>
                      }
                    />
                  ))}
                </div>
              </RegionBody>
            </Card>
          )}
          {statsAny && (
            <div className="order-1 grid grid-cols-1 gap-6 @xl:grid-cols-3 @4xl:order-2 @4xl:grid-cols-1 @4xl:content-start">
              {visible('groupToday') && (
                <Card>
                  <CardContent>
                    <TodayStat />
                  </CardContent>
                </Card>
              )}
              {visible('groupMonth') && (
                <Card>
                  <CardContent>
                    <MonthStat />
                  </CardContent>
                </Card>
              )}
              {visible('storeCount') && (
                <Card>
                  <CardContent>
                    <StoreCountStat />
                  </CardContent>
                </Card>
              )}
            </div>
          )}
          {!any && <NothingToShow />}
        </div>
      </div>
    </div>
  );
}

// ---- 案 C: 数字は 1 枚のカードに区切って横並び。順位は全店を、首位比の棒・状態付きで縦に並べ切る。

function VariantC() {
  const visible = useVisible();
  const { stores } = useData();
  const top = stores[0]?.monthSales || 1;
  const stats = ['groupToday', 'groupMonth', 'storeCount'].filter(visible);
  const more = ['remuneration', 'advertising', 'members'].filter(visible);
  const ops = ['tasks', 'memberNotifications', 'audit'].filter(visible);
  const any = stats.length > 0 || more.length > 0 || ops.length > 0 || visible('ranking');
  // 1 枚のカードの中は幅で折り返す。固定 3 列だと 1024px で 8 桁の金額がはみ出る。
  const strip = 'grid grid-cols-[repeat(auto-fit,minmax(15rem,1fr))] gap-x-8 gap-y-6';
  return (
    <div className="space-y-6">
      <Heading />
      {stats.length > 0 && (
        <Card>
          <CardContent>
            <div className={strip}>
              {visible('groupToday') && <TodayStat />}
              {visible('groupMonth') && <MonthStat />}
              {visible('storeCount') && <StoreCountStat />}
            </div>
          </CardContent>
        </Card>
      )}
      {more.length > 0 && (
        <Card>
          <CardContent>
            <div className={strip}>
              {visible('remuneration') && (
                <RegionBody
                  region="remuneration"
                  failMessage="今月の報酬の取得に失敗しました"
                  skeleton={<StatSkeleton />}
                >
                  <Stat
                    note={`今月の報酬（${MONTH_LABEL}）`}
                    value={formatMoney(groupMonthRemuneration)}
                    sub="保証・ボーナスを含む"
                  />
                </RegionBody>
              )}
              {visible('advertising') && (
                <RegionBody
                  region="advertising"
                  failMessage="今月の広告費の取得に失敗しました"
                  skeleton={<StatSkeleton />}
                >
                  <Stat note="今月の広告費（11月分）" value={formatMoney(groupMonthAdvertising)} />
                </RegionBody>
              )}
              {visible('members') && (
                <RegionBody
                  region="members"
                  failMessage="会員数の取得に失敗しました"
                  skeleton={<StatSkeleton />}
                >
                  <Stat
                    note="会員"
                    value={`${groupMembers.toLocaleString()} 人`}
                    sub="有効な会員"
                  />
                </RegionBody>
              )}
            </div>
          </CardContent>
        </Card>
      )}
      {visible('ranking') && (
        <Card className="gap-0 py-0">
          <CardHeader className="py-4">
            <CardTitle role="heading" aria-level={2}>
              店舗別の今月の売上
            </CardTitle>
          </CardHeader>
          <RegionBody
            region="ranking"
            failMessage="店舗別の売上の取得に失敗しました"
            skeleton={<RankSkeleton rows={6} />}
          >
            <ol className="divide-y border-t">
              {stores.map((s, i) => (
                <li key={s.id}>
                  <Link
                    href="#"
                    className="grid grid-cols-[2rem_minmax(0,1fr)_minmax(6rem,14rem)_7rem] items-center gap-3 px-6 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
                  >
                    <span className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary-strong tabular-nums">
                      {i + 1}
                    </span>
                    <span className="flex min-w-0 items-center gap-2">
                      <span className="truncate text-sm text-foreground" title={s.name}>
                        {s.name}
                      </span>
                      {s.status === 'PREPARING' && <StoreStatusBadge status={s.status} />}
                    </span>
                    <span className="h-2 rounded-full bg-muted" aria-hidden>
                      <span
                        className="block h-2 rounded-full bg-primary-strong"
                        style={{ width: `${(s.monthSales / top) * 100}%` }}
                      />
                    </span>
                    <span className="text-right text-sm font-medium tabular-nums text-foreground">
                      {formatMoney(s.monthSales)}
                    </span>
                  </Link>
                </li>
              ))}
            </ol>
          </RegionBody>
        </Card>
      )}
      {ops.length > 0 && (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(18rem,1fr))] gap-6">
          {visible('tasks') && (
            <RecentList
              region="tasks"
              title="最近の定期処理"
              rows={groupRecentTasks}
              failMessage="定期処理の取得に失敗しました"
            />
          )}
          {visible('memberNotifications') && (
            <RecentList
              region="memberNotifications"
              title="最近の会員宛の通知"
              rows={groupRecentMemberNotifications}
              failMessage="会員宛の通知の取得に失敗しました"
            />
          )}
          {visible('audit') && (
            <RecentList
              region="audit"
              title="最近の操作（監査）"
              rows={groupRecentAudit}
              failMessage="監査の取得に失敗しました"
            />
          )}
        </div>
      )}
      {!any && <NothingToShow />}
    </div>
  );
}
