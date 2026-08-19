'use client';

import { CalendarDaysIcon, ClockIcon, InboxIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
import { notify } from '@/shared/notify';
import { CastResponse, castApi } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';
import { getApiErrorMessage, useResource } from '@/shared/lib';
import { RegionError, Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui';
import { monthRange, toDateStr } from '../lib/datetime';
import { ShiftCalendar } from './ShiftCalendar';
import { ShiftFormModal } from './ShiftFormModal';
import { ShiftPublicationPanel } from './ShiftPublicationPanel';
import { ShiftRequestInbox } from './ShiftRequestInbox';
import { ShiftTimeline } from './ShiftTimeline';

const CALENDAR_TAB = 'calendar';
const TIMELINE_TAB = 'timeline';
const REQUESTS_TAB = 'requests';

/** 出勤管理ページ。カレンダー俯瞰・日別タイムライン・出勤希望 inbox をタブで切り替える。 */
export default function ShiftsPage() {
  const [tab, setTab] = useState<string>(CALENDAR_TAB);
  const [month, setMonth] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const [selectedDate, setSelectedDate] = useState(() => toDateStr(new Date()));
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ShiftResponse | null>(null);
  const [publishing, setPublishing] = useState(false);

  // キャスト一覧（フォームの選択肢 + タイムラインの名前解決）。101 人以上でも氏名を解決できるよう
  // 全ページ取得する。途中まで読めた分は失敗として捨てられる — 欠けた名簿は「そのキャストは
  // 居ない」と読める
  const {
    data: castsData,
    failure: castsFailure,
    reload: loadAllCasts,
  } = useResource(async () => {
    const size = 200;
    const all: CastResponse[] = [];
    for (let page = 0; ; page += 1) {
      const res = await castApi.list({ page, size, sort: 'displayOrder,id,asc' });
      all.push(...res.rows);
      if (res.rows.length < size) break; // 最終ページ
    }
    return all;
  });
  const casts = castsData ?? [];

  // 表示中のタブに応じた取得区間（カレンダー = 月、タイムライン = 単日）
  const range = useMemo(
    () => (tab === CALENDAR_TAB ? monthRange(month) : { from: selectedDate, to: selectedDate }),
    [tab, month, selectedDate]
  );

  // 素早い月切替・日クリックで古いリクエストが後から解決しても、現在の range に対応する
  // 応答だけが反映される（フックのリクエスト連番による stale 応答の破棄）。
  const {
    data: shiftsData,
    setData: setShiftsData,
    isLoading: loading,
    failure: shiftsFailure,
    reload: reloadShifts,
  } = useResource(() => shiftApi.list(range), [range]);
  // 読めなかった区間を空のまま見せると「この月は誰も出勤しない」に化けるため、失敗時は
  // 一覧そのものを出さない（下の失敗態へ倒す）
  const shifts = shiftsData ?? [];

  /**
   * 公開可否を切り替える。行内の目玉とパネルの Switch・一括はすべてここへ入る — 二つの入口が
   * 同じ状態を映すのは、切替がこの一箇所を通るから。
   *
   * <p>一括は UI 上の操作にすぎず、データは逐行更新する（一括エンドポイントは無い — ADR 0015）。
   * 途中で落ちた行があっても成功した行だけを差し替えるので、画面はそのあとも本当のことを言う。
   */
  const changePublication = async (targets: ShiftResponse[], published: boolean) => {
    const ids = targets.map(s => s.id).filter((id): id is string => id !== undefined);
    if (ids.length === 0) return;
    setPublishing(true);
    try {
      const results = await Promise.allSettled(
        ids.map(id => shiftApi.changePublication(id, published))
      );
      const updated = new Map<string, ShiftResponse>();
      for (const r of results) {
        if (r.status === 'fulfilled' && r.value?.id !== undefined) updated.set(r.value.id, r.value);
      }
      // 取り直しに倒すと、切り替えたばかりのタイムラインが読み込み表示で一瞬消える
      if (updated.size > 0) {
        setShiftsData(rows =>
          (rows ?? []).map(s => (s.id !== undefined ? (updated.get(s.id) ?? s) : s))
        );
      }
      const rejected = results.filter(r => r.status === 'rejected');
      if (rejected.length === 0) {
        notify.success(`${ids.length}件のシフトを${published ? '公開' : '非公開に'}しました`);
      } else if (rejected.length === ids.length) {
        notify.error(getApiErrorMessage(rejected[0].reason, '公開状態の変更に失敗しました'));
      } else {
        notify.error(`${rejected.length}件のシフトの公開状態を変更できませんでした`);
      }
    } finally {
      setPublishing(false);
    }
  };

  const openAdd = () => {
    setEditing(null);
    setModalOpen(true);
  };
  const openEdit = (shift: ShiftResponse) => {
    setEditing(shift);
    setModalOpen(true);
  };
  const selectDay = (date: string) => {
    setSelectedDate(date);
    setTab(TIMELINE_TAB);
  };

  // カレンダーは取得失敗で器ごと失敗態に差し替える。タイムラインは日付ナビが復旧経路を
  // 兼ねる（別の日へ動けば取り直す）ため、ヘッダを残して本体だけが失敗を名乗る。
  const shiftsError = (
    <RegionError
      message="シフトの取得に失敗しました"
      onRetry={() => void reloadShifts()}
      className="justify-center p-8"
    />
  );

  // 既定・ホバーの文字色はプリミティブに任せ、選択中だけをプライマリの下線と文字色で示す。
  const tabTriggerClass =
    'h-auto flex-none rounded-none border-0 border-b-2 border-transparent px-1 py-3 text-sm font-medium shadow-none after:hidden data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:text-primary-strong data-[state=active]:shadow-none';

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">出勤管理</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          キャストの出勤シフトを登録・確認できます。
        </p>
      </div>

      {/* 名簿は 3 つの子（タイムラインの名前解決・フォームの選択肢・inbox の申請者名）へ渡るため、
          失敗はどれか 1 つの中ではなく頁の高さで名乗る */}
      {castsFailure !== null && (
        <RegionError message="キャストの取得に失敗しました" onRetry={() => void loadAllCasts()} />
      )}

      <Tabs value={tab} onValueChange={setTab} className="gap-0">
        <TabsList
          variant="line"
          className="h-auto w-full justify-start gap-6 rounded-none border-b p-0"
        >
          <TabsTrigger value={CALENDAR_TAB} className={tabTriggerClass}>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDaysIcon className="h-4 w-4" />
              カレンダー
            </span>
          </TabsTrigger>
          <TabsTrigger value={TIMELINE_TAB} className={tabTriggerClass}>
            <span className="inline-flex items-center gap-1.5">
              <ClockIcon className="h-4 w-4" />
              タイムライン
            </span>
          </TabsTrigger>
          <TabsTrigger value={REQUESTS_TAB} className={tabTriggerClass}>
            <span className="inline-flex items-center gap-1.5">
              <InboxIcon className="h-4 w-4" />
              出勤希望
            </span>
          </TabsTrigger>
        </TabsList>
        <TabsContent value={CALENDAR_TAB} className="mt-6">
          {shiftsFailure !== null ? (
            shiftsError
          ) : (
            <ShiftCalendar
              month={month}
              shifts={shifts}
              onPrevMonth={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              onNextMonth={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              onSelectDate={selectDay}
            />
          )}
        </TabsContent>
        <TabsContent value={TIMELINE_TAB} className="mt-6 space-y-6">
          <ShiftTimeline
            date={selectedDate}
            shifts={shifts}
            casts={casts}
            loading={loading}
            failed={shiftsFailure !== null}
            onRetry={() => void reloadShifts()}
            onChangeDate={setSelectedDate}
            onAddShift={openAdd}
            onEditShift={openEdit}
            publishing={publishing}
            onChangePublication={(targets, published) => void changePublication(targets, published)}
          />
          {/* 取得失敗はタイムラインが名乗る。同じ取得を映すパネルにまで二重に名乗らせず、
              読めていない間は 0 件と読める姿も出さない。空の日もタイムラインが名乗る側 */}
          {shiftsFailure === null && !loading && shifts.length > 0 && (
            <ShiftPublicationPanel
              shifts={shifts}
              casts={casts}
              busy={publishing}
              onChangePublication={(targets, published) =>
                void changePublication(targets, published)
              }
            />
          )}
        </TabsContent>
        <TabsContent value={REQUESTS_TAB} className="mt-6">
          <ShiftRequestInbox casts={casts} onApproved={() => void reloadShifts()} />
        </TabsContent>
      </Tabs>

      <ShiftFormModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        casts={casts}
        editing={editing}
        defaultDate={selectedDate}
        onSaved={() => void reloadShifts()}
      />
    </div>
  );
}
