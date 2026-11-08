import { recentMonths } from '@/shared/lib';

const MONTHLY_PATH = '/cast/remunerations/monthly';

/** 月次明細を店舗と月を指して開く URL。 */
export function monthlyHref(storeId: number, month: string): string {
  return `${MONTHLY_PATH}?${new URLSearchParams({ store_id: String(storeId), month })}`;
}

/** URL の月。月の切替で選べない値（書式違い・直近 12 か月の外）は今月にする。 */
export function queryMonth(value: string | null, thisMonth: string): string {
  return value !== null && recentMonths(thisMonth).includes(value) ? value : thisMonth;
}
