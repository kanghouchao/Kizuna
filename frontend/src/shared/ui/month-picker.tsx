'use client';

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { Button } from './button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './select';

/** 'YYYY-MM' を n か月ずらす。 */
function shiftMonth(month: string, n: number): string {
  const [y, m] = month.split('-').map(Number);
  const index = y * 12 + (m - 1) + n;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}

function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return `${y}年${m}月`;
}

interface MonthPickerProps {
  /** 'YYYY-MM'。 */
  value: string;
  /** 今月 'YYYY-MM'。選べるのはここから遡る 12 か月。 */
  currentMonth: string;
  onValueChange: (month: string) => void;
}

/** 「‹ 2026年11月 ›」。中央を押すと直近 12 か月の一覧から選ぶ。年月を手で打たせない。 */
export function MonthPicker({ value, currentMonth, onValueChange }: MonthPickerProps) {
  const months = Array.from({ length: 12 }, (_, i) => shiftMonth(currentMonth, -i));
  const items = months.map(month => ({ value: month, label: monthLabel(month) }));
  const oldest = months[months.length - 1];
  // 移動先が範囲内のときだけ押せる。範囲外の value からは一覧で選び直させる。
  const inRange = (month: string) => month >= oldest && month <= currentMonth;
  const prev = shiftMonth(value, -1);
  const next = shiftMonth(value, 1);

  return (
    <div className="flex items-center justify-between gap-2">
      <Button
        type="button"
        variant="ghost"
        aria-label="前の月"
        className="size-11"
        disabled={!inRange(prev)}
        onClick={() => onValueChange(prev)}
      >
        <ChevronLeftIcon className="size-6" aria-hidden="true" />
      </Button>
      <Select
        items={items}
        value={value}
        onValueChange={month => {
          if (month !== null) onValueChange(month);
        }}
      >
        <SelectTrigger className="border-none text-base font-semibold shadow-none data-[size=default]:h-11">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {items.map(item => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Button
        type="button"
        variant="ghost"
        aria-label="次の月"
        className="size-11"
        disabled={!inRange(next)}
        onClick={() => onValueChange(next)}
      >
        <ChevronRightIcon className="size-6" aria-hidden="true" />
      </Button>
    </div>
  );
}
