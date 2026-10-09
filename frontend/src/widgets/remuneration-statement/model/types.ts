import type { Page, CursorPage } from '@/shared/api';
export type StatementScope =
  | { scope: 'store'; personId: number }
  | { scope: 'platform'; personId: number; storeId: number }
  | { scope: 'self'; storeId: number };
export interface Guarantee {
  id: string;
  person_id: number;
  effective_from: string;
  state: 'ACTIVE' | 'STOPPED';
  daily_amount?: number;
  reason: string;
  cancelled_at?: string;
}
export interface Bonus {
  id: string;
  person_id: number;
  award_date: string;
  amount: number;
  effective_amount: number;
  reason: string;
  version: number;
  cancelled_at?: string;
}
export interface GuaranteeList {
  version: number;
  entries: Page<Guarantee>;
}
export interface Change {
  id: string;
  actor_id: number;
  action: string;
  reason: string;
  before_value?: Guarantee | Bonus;
  after_value?: Guarantee | Bonus;
  created_at: string;
}
export type Changes = CursorPage<Change>;
export interface Day {
  business_date: string;
  order_amount: number;
  guarantee_state?: 'ACTIVE' | 'STOPPED';
  daily_amount?: number;
  closed_duration: string;
  attendance_incomplete: boolean;
  guarantee_status:
    'NOT_ELIGIBLE' | 'PENDING_ATTENDANCE' | 'NOT_CONFIGURED' | 'STOPPED' | 'CALCULATED';
  guarantee_amount?: number;
  bonus_amount: number;
}
export interface Statement {
  store_id: number;
  store_name: string;
  person_id: number;
  name: string;
  month: string;
  generated_at: string;
  order_total: number;
  known_guarantee_total: number;
  guarantee_total?: number;
  bonus_total: number;
  total?: number;
  days: Day[];
  orders: Page<{
    order_id: string;
    business_date: string;
    service_summary: string;
    accrued_remuneration: number;
    completion_invalidated: boolean;
  }>;
  bonus_awards: Page<{
    id: string;
    award_date: string;
    effective_amount: number;
    reason: string;
    cancelled: boolean;
  }>;
}
export interface GuaranteeInput {
  effective_from: string;
  state: 'ACTIVE' | 'STOPPED';
  daily_amount: number | null;
  reason: string;
  expected_version: number;
  request_id: string;
}
export interface BonusInput {
  award_date: string;
  amount: number;
  reason: string;
  request_id: string;
}
export interface CancellationInput {
  reason: string;
  expected_version: number;
  request_id: string;
}
