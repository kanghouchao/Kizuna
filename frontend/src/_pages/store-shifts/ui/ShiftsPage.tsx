'use client';

import { Tab, TabGroup, TabList, TabPanel, TabPanels } from '@headlessui/react';
import { CalendarDaysIcon, ClockIcon } from '@heroicons/react/24/outline';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastResponse, castApi } from '@/entities/cast';
import { ShiftResponse, shiftApi } from '@/entities/shift';
import { monthRange, toDateStr } from '../lib/datetime';
import { ShiftCalendar } from './ShiftCalendar';
import { ShiftFormModal } from './ShiftFormModal';
import { ShiftTimeline } from './ShiftTimeline';

const CALENDAR_TAB = 0;
const TIMELINE_TAB = 1;

/** 出勤管理ページ。カレンダー俯瞰と日別タイムラインをタブで切り替える。 */
export default function ShiftsPage() {
  const [tab, setTab] = useState(CALENDAR_TAB);
  const [month, setMonth] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const [selectedDate, setSelectedDate] = useState(() => toDateStr(new Date()));
  const [shifts, setShifts] = useState<ShiftResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [casts, setCasts] = useState<CastResponse[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ShiftResponse | null>(null);

  // キャスト一覧（フォームの選択肢 + タイムラインの名前解決）
  useEffect(() => {
    castApi
      .list({ size: 100, sort: 'displayOrder,asc' })
      .then(page => setCasts(page.content))
      .catch(() => toast.error('キャストの取得に失敗しました'));
  }, []);

  // 表示中のタブに応じた取得区間（カレンダー = 月、タイムライン = 単日）
  const range = useMemo(
    () => (tab === CALENDAR_TAB ? monthRange(month) : { from: selectedDate, to: selectedDate }),
    [tab, month, selectedDate]
  );

  const loadShifts = useCallback(async () => {
    setLoading(true);
    try {
      setShifts(await shiftApi.list(range));
    } catch {
      toast.error('シフトの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    void loadShifts();
  }, [loadShifts]);

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

  const tabClass = (selected: boolean) =>
    `border-b-2 px-1 py-3 text-sm font-medium outline-none ${
      selected
        ? 'border-blue-600 text-blue-600'
        : 'border-transparent text-gray-500 hover:text-gray-700'
    }`;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">出勤管理</h1>
        <p className="mt-1 text-sm text-gray-500">キャストの出勤シフトを登録・確認できます。</p>
      </div>

      <TabGroup selectedIndex={tab} onChange={setTab}>
        <TabList className="flex gap-6 border-b border-gray-200">
          <Tab className={({ selected }) => tabClass(selected)}>
            <span className="inline-flex items-center gap-1.5">
              <CalendarDaysIcon className="h-4 w-4" />
              カレンダー
            </span>
          </Tab>
          <Tab className={({ selected }) => tabClass(selected)}>
            <span className="inline-flex items-center gap-1.5">
              <ClockIcon className="h-4 w-4" />
              タイムライン
            </span>
          </Tab>
        </TabList>
        <TabPanels className="mt-6">
          <TabPanel>
            <ShiftCalendar
              month={month}
              shifts={shifts}
              onPrevMonth={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() - 1, 1))}
              onNextMonth={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() + 1, 1))}
              onSelectDate={selectDay}
            />
          </TabPanel>
          <TabPanel>
            <ShiftTimeline
              date={selectedDate}
              shifts={shifts}
              casts={casts}
              loading={loading}
              onAddShift={openAdd}
              onEditShift={openEdit}
            />
          </TabPanel>
        </TabPanels>
      </TabGroup>

      <ShiftFormModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        casts={casts}
        editing={editing}
        defaultDate={selectedDate}
        onSaved={loadShifts}
      />
    </div>
  );
}
