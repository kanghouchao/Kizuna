'use client';

import { CalendarDaysIcon, ClockIcon, InboxIcon } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CastResponse, castApi } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';
import { RegionError, Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui';
import { monthRange, toDateStr } from '../lib/datetime';
import { ShiftCalendar } from './ShiftCalendar';
import { ShiftFormModal } from './ShiftFormModal';
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
  const [shifts, setShifts] = useState<ShiftResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [shiftsFailed, setShiftsFailed] = useState(false);
  const [casts, setCasts] = useState<CastResponse[]>([]);
  const [castsFailed, setCastsFailed] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ShiftResponse | null>(null);
  // 並行リクエストが順不同で完了しても、最新のリクエストだけが state を更新する。失敗が
  // 名簿をクリアするので、在途の古い失敗が新しい成功を消し得る
  // （シフト側の同じ守衛は effect 内の ignore が担う）
  const castsRequestIdRef = useRef(0);

  // キャスト一覧（フォームの選択肢 + タイムラインの名前解決）。101 人以上でも氏名を解決できるよう全ページ取得する。
  const loadAllCasts = useCallback(async () => {
    const requestId = ++castsRequestIdRef.current;
    // 再試行の前に畳む。同じ姿のまま失敗を繰り返すと role="alert" が挿入されず、二度目の
    // 失敗が読み上げ利用者に届かない
    setCastsFailed(false);
    try {
      const size = 200;
      const all: CastResponse[] = [];
      for (let page = 0; ; page += 1) {
        const res = await castApi.list({ page, size, sort: 'displayOrder,id,asc' });
        all.push(...res.rows);
        if (res.rows.length < size) break; // 最終ページ
      }
      if (requestId === castsRequestIdRef.current) {
        setCasts(all);
        setCastsFailed(false);
      }
    } catch {
      // 途中まで読めた分も捨てる — 欠けた名簿は「そのキャストは居ない」と読める
      if (requestId === castsRequestIdRef.current) {
        setCasts([]);
        setCastsFailed(true);
      }
    }
  }, []);

  useEffect(() => {
    void loadAllCasts();
  }, [loadAllCasts]);

  // 表示中のタブに応じた取得区間（カレンダー = 月、タイムライン = 単日）
  const range = useMemo(
    () => (tab === CALENDAR_TAB ? monthRange(month) : { from: selectedDate, to: selectedDate }),
    [tab, month, selectedDate]
  );

  // range 変更や保存後の再取得。素早い月切替・日クリックで古いリクエストが後から解決しても、
  // 現在の range に対応する応答だけを反映する（stale 応答による上書き防止）。
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    // 取り直しの前に畳む（役割は上の setCastsFailed(false) と同じ）
    setShiftsFailed(false);
    shiftApi
      .list(range)
      .then(result => {
        if (!ignore) {
          setShifts(result);
          setShiftsFailed(false);
        }
      })
      .catch(() => {
        // 読めなかった区間を空のまま見せると「この月は誰も出勤しない」に化ける
        if (!ignore) {
          setShifts([]);
          setShiftsFailed(true);
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [range, reloadKey]);

  const reloadShifts = () => setReloadKey(key => key + 1);

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

  // シフトはカレンダーとタイムラインの二つの器に描かれる。どちらの器も同じ 1 件の取得の
  // 産物なので、失敗の姿も同じものを置く。
  const shiftsError = (
    <RegionError
      message="シフトの取得に失敗しました"
      onRetry={reloadShifts}
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
      {castsFailed && (
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
          {shiftsFailed ? (
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
        <TabsContent value={TIMELINE_TAB} className="mt-6">
          {shiftsFailed ? (
            shiftsError
          ) : (
            <ShiftTimeline
              date={selectedDate}
              shifts={shifts}
              casts={casts}
              loading={loading}
              onAddShift={openAdd}
              onEditShift={openEdit}
            />
          )}
        </TabsContent>
        <TabsContent value={REQUESTS_TAB} className="mt-6">
          <ShiftRequestInbox casts={casts} onApproved={reloadShifts} />
        </TabsContent>
      </Tabs>

      <ShiftFormModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        casts={casts}
        editing={editing}
        defaultDate={selectedDate}
        onSaved={reloadShifts}
      />
    </div>
  );
}
