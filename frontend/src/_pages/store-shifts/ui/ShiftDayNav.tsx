'use client';

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { Button, Input } from '@/shared/ui';
import { addDaysStr, toDateStr } from '../lib/datetime';

interface ShiftDayNavProps {
  /** 表示日 'yyyy-MM-dd'。 */
  date: string;
  onChangeDate: (date: string) => void;
}

/** クイックジャンプは選択中の日ではなく実際の今日を基準にする。 */
const QUICK_JUMPS = [
  { label: '昨日', offset: -1 },
  { label: '今日', offset: 0 },
  { label: '明日', offset: 1 },
  { label: '明後日', offset: 2 },
] as const;

/**
 * 単日の器が共有する日付ナビ。タイムラインと当日実績は同じ日を映す二つの面なので、送り・戻し・
 * クイックジャンプの挙動も一つの組み立てから出す。
 */
export function ShiftDayNav({ date, onChangeDate }: ShiftDayNavProps) {
  return (
    <>
      <div className="flex items-center gap-1">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={() => onChangeDate(addDaysStr(date, -1))}
          aria-label="前日"
        >
          <ChevronLeftIcon />
        </Button>
        {/* クリアで空文字が飛んでくるため無視する（表示日が無い状態は作らない） */}
        <Input
          type="date"
          value={date}
          onChange={e => {
            if (e.target.value) onChangeDate(e.target.value);
          }}
          aria-label="表示する日付"
          className="w-40"
        />
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          onClick={() => onChangeDate(addDaysStr(date, 1))}
          aria-label="翌日"
        >
          <ChevronRightIcon />
        </Button>
      </div>
      <div className="flex items-center gap-1">
        {QUICK_JUMPS.map(({ label, offset }) => (
          <Button
            key={label}
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onChangeDate(addDaysStr(toDateStr(new Date()), offset))}
          >
            {label}
          </Button>
        ))}
      </div>
    </>
  );
}
