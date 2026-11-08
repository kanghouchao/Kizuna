import type { MonthlyRemuneration } from './monthlyRemuneration';

export interface DailyRemuneration extends Omit<MonthlyRemuneration, 'month'> {
  business_date: string;
}
