import type { MonthlyRemuneration } from './monthlyRemuneration';
export interface PlatformMonthlyRemunerationStore {
  store_id: number;
  store_name: string;
}
export interface PlatformMonthlyRemuneration extends MonthlyRemuneration {
  store_id: number;
  store_name: string;
}
