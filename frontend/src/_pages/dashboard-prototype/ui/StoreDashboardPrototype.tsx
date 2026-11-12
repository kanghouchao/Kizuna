'use client';

import { ChevronRightIcon } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { useStoreContext } from '@/entities/user';
import { formatMoney } from '@/shared/lib';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/shared/ui';
import { PrototypeProvider, useProto, type Preset, type RegionDef, type Variant } from './controls';
import {
  MONTH_LABEL,
  PREV_MONTH_LABEL,
  TODAY_LABEL,
  storeApplicantsInterview,
  storeAttendance,
  storeMonthAdvertising,
  storeMonthRemuneration,
  storeMonthSales,
  storeRecentNotifications,
  storeOrders,
  storePrevMonthSales,
  storeTodaySales,
  storeTodos,
} from './fake-data';
import {
  AsOf,
  DailyChart,
  Delta,
  NumLink,
  RecentList,
  RegionBody,
  Stat,
  StatSkeleton,
  StoreStatusBadge,
  useVisible,
} from './parts';

// 試作: 店舗のダッシュボードの 3 案。?variant=A|B|C で切り替える。
const VARIANTS: Variant[] = [
  { key: 'A', name: '上下二段：今日→今月' },
  { key: 'B', name: '左右：数字｜やること' },
  { key: 'C', name: 'タブ：今日｜今月' },
];

const REPORT = ['OPERATIONAL_REPORT_VIEW', 'ORDER_MANAGE'];
const REGIONS: RegionDef[] = [
  { key: 'todayOrders', label: '今日の受注件数', perms: ['ORDER_MANAGE'] },
  { key: 'todaySales', label: '今日の売上', perms: REPORT },
  { key: 'monthSales', label: '今月の売上', perms: REPORT },
  { key: 'dailyChart', label: '日別推移', perms: REPORT },
  { key: 'orderApplications', label: '予約申請', perms: ['ORDER_MANAGE'] },
  { key: 'shiftRequests', label: 'シフト申請', perms: ['SHIFT_MANAGE'] },
  { key: 'reviews', label: '口コミ承認待ち', perms: ['REVIEW_VIEW'] },
  {
    key: 'remuneration',
    label: '今月の報酬',
    perms: [...REPORT, 'REMUNERATION_VIEW'],
    candidate: true,
  },
  { key: 'advertising', label: '今月の広告費', perms: ['ADVERTISING_COST_VIEW'], candidate: true },
  { key: 'attendance', label: '今日の出勤', perms: ['SHIFT_MANAGE'], candidate: true },
  { key: 'applicants', label: '面接待ちの応募者', perms: ['RECRUITMENT_VIEW'], candidate: true },
  { key: 'notifications', label: '顧客宛の通知', perms: ['NOTIFICATION_VIEW'], candidate: true },
];

// 店長・スタッフは今の種子の権限（ダッシュボードに関わる分）。ほかはカスタムロールの例。
const PRESETS: Preset[] = [
  {
    label: '店長（既定）',
    perms: [
      ...REPORT,
      'SHIFT_MANAGE',
      'REVIEW_VIEW',
      'REMUNERATION_VIEW',
      'ADVERTISING_COST_VIEW',
      'RECRUITMENT_VIEW',
      'NOTIFICATION_VIEW',
    ],
  },
  {
    label: 'スタッフ（既定）',
    perms: ['ORDER_MANAGE', 'SHIFT_MANAGE', 'REVIEW_VIEW', 'NOTIFICATION_VIEW'],
  },
  { label: '例: 受付', perms: ['ORDER_MANAGE'] },
  { label: '例: 採用担当', perms: ['RECRUITMENT_VIEW', 'SHIFT_MANAGE'] },
];

const ORDER_STATUSES = [
  { key: 'reserved', label: '予約' },
  { key: 'serving', label: '接客中' },
  { key: 'completed', label: '完了' },
  { key: 'cancelled', label: '取消' },
] as const;

const TODOS = [
  { key: 'orderApplications', label: '未処理の予約申請', count: storeTodos.orderApplications },
  { key: 'shiftRequests', label: '未処理のシフト申請', count: storeTodos.shiftRequests },
  { key: 'reviews', label: '承認待ちの口コミ', count: storeTodos.reviews },
] as const;

export function StoreDashboardPrototype({ variant }: { variant: string }) {
  return (
    <PrototypeProvider
      regions={REGIONS}
      presets={PRESETS}
      variants={VARIANTS}
      current={variant}
      showStoreOptions="store"
    >
      {variant === 'A' ? <VariantA /> : variant === 'C' ? <VariantC /> : <VariantB />}
    </PrototypeProvider>
  );
}

// ---- 共通の小物（見出し・各区画の中身）。枠と配置は各案が持つ。

function PageHeading({ sub }: { sub?: ReactNode }) {
  const { currentStore } = useStoreContext();
  const { storeCard } = useProto();
  if (storeCard) {
    return (
      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-foreground">ダッシュボード</h1>
        {sub}
      </div>
    );
  }
  return (
    <div className="space-y-1">
      <div className="flex min-w-0 items-center gap-3">
        <h1 className="truncate text-2xl font-bold text-foreground" title={currentStore?.name}>
          {currentStore?.name}
        </h1>
        {currentStore?.status && <StoreStatusBadge status={currentStore.status} />}
      </div>
      {sub}
    </div>
  );
}

/** 旧来の「店舗名と状態」カード。試作の切替で残すかを比べる。 */
function StoreCard({ className }: { className?: string }) {
  const { currentStore } = useStoreContext();
  const { storeCard } = useProto();
  if (!storeCard) return null;
  return (
    <Card className={className}>
      <CardContent>
        <dl>
          <dt className="text-sm font-medium text-muted-foreground">店舗</dt>
          <dd className="mt-1 flex items-center gap-2">
            <span className="truncate text-lg font-medium text-foreground">
              {currentStore?.name}
            </span>
            {currentStore?.status && <StoreStatusBadge status={currentStore.status} />}
          </dd>
        </dl>
      </CardContent>
    </Card>
  );
}

function MonthSalesStat() {
  const { monthDown } = useProto();
  const before = monthDown ? 3_050_000 : storePrevMonthSales;
  return (
    <Stat
      note={`今月の売上（${MONTH_LABEL}）`}
      value={formatMoney(storeMonthSales)}
      delta={<Delta now={storeMonthSales} before={before} label={PREV_MONTH_LABEL} />}
    />
  );
}

function TodaySalesStat() {
  return (
    <Stat
      note="今日の売上"
      value={formatMoney(storeTodaySales.amount)}
      sub={`完了 ${storeTodaySales.count} 件`}
    />
  );
}

const TODO_ROWS = [
  ...TODOS,
  { key: 'applicants', label: '面接待ちの応募者', count: storeApplicantsInterview },
];

function TodoRow({ label, count, unit = '件' }: { label: string; count: number; unit?: string }) {
  return (
    <Link
      href="#"
      className="flex items-center gap-3 px-6 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
    >
      <span className="flex-1 text-sm text-foreground">{label}</span>
      <span className="text-lg font-bold tabular-nums text-foreground">{count}</span>
      <span className="text-sm text-muted-foreground">{unit}</span>
      <ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
    </Link>
  );
}

function RowSkeleton() {
  return (
    <div className="flex items-center gap-3 px-6 py-3">
      <Skeleton className="h-4 flex-1" />
      <Skeleton className="h-6 w-10" />
    </div>
  );
}

// ---- 案 A: 上下二段。「今日」の段（受注件数・今日の売上・やること）→「今月」の段（売上・推移）。

function VariantA() {
  const visible = useVisible();
  const todayAny = ['todayOrders', 'todaySales', ...TODOS.map(t => t.key)].some(visible);
  const monthAny = visible('monthSales') || visible('dailyChart');
  return (
    <div className="space-y-8">
      <PageHeading />
      <StoreCard className="max-w-md" />
      {todayAny && (
        <section className="space-y-3">
          <div className="flex items-baseline justify-between gap-4">
            <h2 className="text-lg font-semibold text-foreground">今日</h2>
            <AsOf>{TODAY_LABEL}</AsOf>
          </div>
          <div className="grid grid-cols-1 gap-6 @container lg:grid-cols-3">
            {visible('todayOrders') && (
              <Card className="lg:col-span-2">
                <CardContent>
                  <p className="mb-3 text-sm text-muted-foreground">今日の受注件数</p>
                  <RegionBody
                    region="todayOrders"
                    failMessage="今日の受注件数の取得に失敗しました"
                    skeleton={<Skeleton className="h-16 w-full" />}
                  >
                    <div className="grid grid-cols-4 divide-x">
                      {ORDER_STATUSES.map(s => (
                        <div key={s.key} className="px-4 first:pl-0">
                          <p className="text-sm text-muted-foreground">{s.label}</p>
                          <p className="text-3xl font-bold tabular-nums text-foreground">
                            <NumLink>{storeOrders[s.key]}</NumLink>
                          </p>
                        </div>
                      ))}
                    </div>
                  </RegionBody>
                </CardContent>
              </Card>
            )}
            {visible('todaySales') && (
              <Card>
                <CardContent>
                  <RegionBody
                    region="todaySales"
                    failMessage="今日の売上の取得に失敗しました"
                    skeleton={<StatSkeleton />}
                  >
                    <TodaySalesStat />
                  </RegionBody>
                </CardContent>
              </Card>
            )}
            {TODOS.filter(t => visible(t.key)).map(t => (
              <Card key={t.key}>
                <CardContent>
                  <RegionBody
                    region={t.key}
                    failMessage={`${t.label}の取得に失敗しました`}
                    skeleton={<StatSkeleton />}
                  >
                    <Stat note={t.label} value={`${t.count} 件`} />
                  </RegionBody>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}
      {monthAny && (
        <section className="space-y-3">
          <div className="flex items-baseline justify-between gap-4">
            <h2 className="text-lg font-semibold text-foreground">今月</h2>
            <AsOf>{MONTH_LABEL}・完了した受注</AsOf>
          </div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {visible('monthSales') && (
              <Card>
                <CardContent>
                  <RegionBody
                    region="monthSales"
                    failMessage="今月の売上の取得に失敗しました"
                    skeleton={<StatSkeleton />}
                  >
                    <MonthSalesStat />
                  </RegionBody>
                </CardContent>
              </Card>
            )}
            {visible('dailyChart') && (
              <Card className={visible('monthSales') ? 'lg:col-span-2' : 'lg:col-span-3'}>
                <CardHeader>
                  <CardTitle role="heading" aria-level={3}>
                    日別の売上
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <RegionBody
                    region="dailyChart"
                    failMessage="日別の売上の取得に失敗しました"
                    skeleton={<Skeleton className="h-56 w-full" />}
                  >
                    <DailyChart className="h-44" />
                  </RegionBody>
                </CardContent>
              </Card>
            )}
          </div>
        </section>
      )}
      {!todayAny && !monthAny && <NothingToShow />}
    </div>
  );
}

// ---- 案 B: 左右。左に数字（売上 2 枚・推移）、右の細い列に「今日やること」（受注件数と未処理）。

function VariantB() {
  const visible = useVisible();
  const numbers = ['todaySales', 'monthSales', 'remuneration', 'advertising'].filter(visible);
  const leftAny = numbers.length > 0 || visible('dailyChart');
  const todos = TODO_ROWS.filter(t => visible(t.key));
  const rightAny =
    visible('todayOrders') || visible('attendance') || visible('notifications') || todos.length > 0;
  return (
    <div className="space-y-6">
      <PageHeading sub={<AsOf>{TODAY_LABEL}</AsOf>} />
      <StoreCard className="max-w-md" />
      <div className="@container">
        <div
          className={
            leftAny && rightAny
              ? 'grid grid-cols-1 gap-6 @4xl:grid-cols-[minmax(0,1fr)_20rem]'
              : 'grid grid-cols-1 gap-6'
          }
        >
          {leftAny && (
            <div className="order-2 min-w-0 space-y-6 @4xl:order-1">
              {numbers.length > 0 && (
                <div className="grid grid-cols-[repeat(auto-fit,minmax(16rem,1fr))] gap-6">
                  {visible('todaySales') && (
                    <Card>
                      <CardContent>
                        <RegionBody
                          region="todaySales"
                          failMessage="今日の売上の取得に失敗しました"
                          skeleton={<StatSkeleton />}
                        >
                          <TodaySalesStat />
                        </RegionBody>
                      </CardContent>
                    </Card>
                  )}
                  {visible('monthSales') && (
                    <Card>
                      <CardContent>
                        <RegionBody
                          region="monthSales"
                          failMessage="今月の売上の取得に失敗しました"
                          skeleton={<StatSkeleton />}
                        >
                          <MonthSalesStat />
                        </RegionBody>
                      </CardContent>
                    </Card>
                  )}
                  {visible('remuneration') && (
                    <Card>
                      <CardContent>
                        <RegionBody
                          region="remuneration"
                          failMessage="今月の報酬の取得に失敗しました"
                          skeleton={<StatSkeleton />}
                        >
                          <Stat
                            note={`今月の報酬（${MONTH_LABEL}）`}
                            value={formatMoney(storeMonthRemuneration)}
                            sub="保証・ボーナスを含む"
                          />
                        </RegionBody>
                      </CardContent>
                    </Card>
                  )}
                  {visible('advertising') && (
                    <Card>
                      <CardContent>
                        <RegionBody
                          region="advertising"
                          failMessage="今月の広告費の取得に失敗しました"
                          skeleton={<StatSkeleton />}
                        >
                          <Stat
                            note="今月の広告費（11月分）"
                            value={formatMoney(storeMonthAdvertising)}
                          />
                        </RegionBody>
                      </CardContent>
                    </Card>
                  )}
                </div>
              )}
              {visible('dailyChart') && (
                <Card>
                  <CardHeader>
                    <CardTitle role="heading" aria-level={2}>
                      日別の売上（{MONTH_LABEL}）
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <RegionBody
                      region="dailyChart"
                      failMessage="日別の売上の取得に失敗しました"
                      skeleton={<Skeleton className="h-64 w-full" />}
                    >
                      <DailyChart className="h-64" />
                    </RegionBody>
                  </CardContent>
                </Card>
              )}
            </div>
          )}
          {rightAny && (
            // 数字の列が無い（売上を見られない）ときは、今日の区画を全幅の格子に並べ直す
            <div
              className={
                leftAny
                  ? 'order-1 space-y-6 @4xl:order-2'
                  : 'grid grid-cols-[repeat(auto-fit,minmax(18rem,1fr))] items-start gap-6'
              }
            >
              {visible('todayOrders') && (
                <Card className="gap-0 py-0">
                  <CardHeader className="py-4">
                    <CardTitle role="heading" aria-level={2}>
                      今日の受注
                    </CardTitle>
                  </CardHeader>
                  <RegionBody
                    region="todayOrders"
                    failMessage="今日の受注件数の取得に失敗しました"
                    skeleton={
                      <>
                        <RowSkeleton />
                        <RowSkeleton />
                        <RowSkeleton />
                        <RowSkeleton />
                      </>
                    }
                  >
                    <div className="divide-y border-t">
                      {ORDER_STATUSES.map(s => (
                        <TodoRow key={s.key} label={s.label} count={storeOrders[s.key]} />
                      ))}
                    </div>
                  </RegionBody>
                </Card>
              )}
              {visible('attendance') && (
                <Card className="gap-0 py-0">
                  <CardHeader className="py-4">
                    <CardTitle role="heading" aria-level={2}>
                      今日の出勤
                    </CardTitle>
                  </CardHeader>
                  <RegionBody
                    region="attendance"
                    failMessage="今日の出勤の取得に失敗しました"
                    skeleton={
                      <>
                        <RowSkeleton />
                        <RowSkeleton />
                        <RowSkeleton />
                      </>
                    }
                  >
                    <div className="divide-y border-t">
                      <TodoRow label="出勤予定" count={storeAttendance.scheduled} unit="人" />
                      <TodoRow label="出勤済み" count={storeAttendance.clockedIn} unit="人" />
                      <TodoRow label="欠勤" count={storeAttendance.absent} unit="人" />
                    </div>
                  </RegionBody>
                </Card>
              )}
              {todos.length > 0 && (
                <Card className="gap-0 py-0">
                  <CardHeader className="py-4">
                    <CardTitle role="heading" aria-level={2}>
                      やること
                    </CardTitle>
                  </CardHeader>
                  <div className="divide-y border-t">
                    {todos.map(t => (
                      <RegionBody
                        key={t.key}
                        region={t.key}
                        failMessage={`${t.label}の取得に失敗しました`}
                        skeleton={<RowSkeleton />}
                      >
                        <TodoRow label={t.label} count={t.count} />
                      </RegionBody>
                    ))}
                  </div>
                </Card>
              )}
              {visible('notifications') && (
                <RecentList
                  region="notifications"
                  title="最近の顧客宛の通知"
                  rows={storeRecentNotifications}
                  failMessage="顧客宛の通知の取得に失敗しました"
                />
              )}
            </div>
          )}
          {!leftAny && !rightAny && <NothingToShow />}
        </div>
      </div>
    </div>
  );
}

// ---- 案 C: タブ。「今日」（受注の大きなタイル・やること・今日の売上）と「今月」（売上・推移）を切り替える。

function VariantC() {
  const visible = useVisible();
  const todayAny = ['todayOrders', 'todaySales', ...TODOS.map(t => t.key)].some(visible);
  const monthAny = visible('monthSales') || visible('dailyChart');
  const todoTotal = TODOS.filter(t => visible(t.key)).reduce((a, t) => a + t.count, 0);
  if (!todayAny && !monthAny) {
    return (
      <div className="space-y-6">
        <PageHeading />
        <NothingToShow />
      </div>
    );
  }
  return (
    <div className="space-y-6">
      <PageHeading />
      <StoreCard className="max-w-md" />
      <Tabs defaultValue={todayAny ? 'today' : 'month'}>
        <TabsList>
          {todayAny && (
            <TabsTrigger value="today">
              今日
              {todoTotal > 0 && (
                <span className="ml-1 rounded-full bg-warning/10 px-1.5 text-xs text-warning-strong tabular-nums">
                  {todoTotal}
                </span>
              )}
            </TabsTrigger>
          )}
          {monthAny && <TabsTrigger value="month">今月</TabsTrigger>}
        </TabsList>
        {todayAny && (
          <TabsContent value="today" className="space-y-6 pt-4">
            <AsOf>{TODAY_LABEL}</AsOf>
            {visible('todayOrders') && (
              <RegionBody
                region="todayOrders"
                failMessage="今日の受注件数の取得に失敗しました"
                skeleton={
                  <div className="grid grid-cols-4 gap-6">
                    {ORDER_STATUSES.map(s => (
                      <Skeleton key={s.key} className="h-28" />
                    ))}
                  </div>
                }
              >
                <div className="grid grid-cols-2 gap-6 lg:grid-cols-4">
                  {ORDER_STATUSES.map(s => (
                    <Card key={s.key}>
                      <CardContent>
                        <Stat note={`今日の${s.label}`} value={`${storeOrders[s.key]} 件`} />
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </RegionBody>
            )}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
              {TODOS.some(t => visible(t.key)) && (
                <Card className="gap-0 py-0 lg:col-span-2">
                  <CardHeader className="py-4">
                    <CardTitle role="heading" aria-level={2}>
                      やること
                    </CardTitle>
                  </CardHeader>
                  <div className="divide-y border-t">
                    {TODOS.filter(t => visible(t.key)).map(t => (
                      <RegionBody
                        key={t.key}
                        region={t.key}
                        failMessage={`${t.label}の取得に失敗しました`}
                        skeleton={<RowSkeleton />}
                      >
                        <TodoRow label={t.label} count={t.count} />
                      </RegionBody>
                    ))}
                  </div>
                </Card>
              )}
              {visible('todaySales') && (
                <Card>
                  <CardContent>
                    <RegionBody
                      region="todaySales"
                      failMessage="今日の売上の取得に失敗しました"
                      skeleton={<StatSkeleton />}
                    >
                      <TodaySalesStat />
                    </RegionBody>
                  </CardContent>
                </Card>
              )}
            </div>
          </TabsContent>
        )}
        {monthAny && (
          <TabsContent value="month" className="space-y-6 pt-4">
            {visible('monthSales') && (
              <Card>
                <CardContent>
                  <RegionBody
                    region="monthSales"
                    failMessage="今月の売上の取得に失敗しました"
                    skeleton={<StatSkeleton />}
                  >
                    <MonthSalesStat />
                  </RegionBody>
                </CardContent>
              </Card>
            )}
            {visible('dailyChart') && (
              <Card>
                <CardHeader>
                  <CardTitle role="heading" aria-level={2}>
                    日別の売上
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <RegionBody
                    region="dailyChart"
                    failMessage="日別の売上の取得に失敗しました"
                    skeleton={<Skeleton className="h-72 w-full" />}
                  >
                    <DailyChart className="h-72" />
                  </RegionBody>
                </CardContent>
              </Card>
            )}
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}

export function NothingToShow() {
  return (
    <div className="rounded-xl border p-8 text-center">
      <p className="text-sm text-foreground">このダッシュボードで表示できる項目がありません。</p>
      <p className="mt-1 text-sm text-muted-foreground">
        表示には管理者からの権限の付与が必要です。
      </p>
    </div>
  );
}
