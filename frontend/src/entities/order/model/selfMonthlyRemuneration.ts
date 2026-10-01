import type { Page } from '@/shared/api';

export interface SelfMonthlyRemunerationStore {
  store_id: number;
  store_name: string;
}

export interface SelfMonthlyRemunerationOrder {
  order_id: string;
  business_date: string;
  service_summary: string;
  accrued_remuneration: number;
  completion_invalidated: boolean;
}

export interface SelfMonthlyRemuneration extends SelfMonthlyRemunerationStore {
  month: string;
  total_remuneration: number;
  orders: Page<SelfMonthlyRemunerationOrder>;
}
