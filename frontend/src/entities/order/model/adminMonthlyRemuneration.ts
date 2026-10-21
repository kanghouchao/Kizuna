import type { MonthlyRemuneration } from './monthlyRemuneration';
export interface AdminMonthlyRemunerationStore {
  store_id: number;
  store_name: string;
}
export interface AdminMonthlyRemuneration extends MonthlyRemuneration {
  store_id: number;
  store_name: string;
}
