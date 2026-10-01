import type { Page } from '@/shared/api';

export interface MonthlyRemunerationCast {
  person_id: number;
  name: string;
}

export interface MonthlyRemunerationOrder {
  order_id: string;
  business_date: string;
  service_summary: string;
  accrued_remuneration: number;
  completion_invalidated: boolean;
}

export interface MonthlyRemuneration {
  person_id: number;
  name: string;
  month: string;
  total_remuneration: number;
  orders: Page<MonthlyRemunerationOrder>;
}
