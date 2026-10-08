import type { MonthlyRemuneration } from './monthlyRemuneration';
import type { SelfMonthlyRemuneration } from './selfMonthlyRemuneration';

export interface DailyRemuneration extends Omit<MonthlyRemuneration, 'month'> {
  business_date: string;
}
export interface SelfDailyRemuneration extends Omit<SelfMonthlyRemuneration, 'month'> {
  business_date: string;
}
