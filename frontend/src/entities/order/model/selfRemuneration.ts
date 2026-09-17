export interface SelfRemunerationEnrollment {
  enrollment_id: string;
  store_id: number;
  store_name: string;
  status: 'ENROLLED' | 'SUSPENDED' | 'WITHDRAWN';
  ended_at?: string;
}
export interface SelfRemunerationSummary {
  order_id: string;
  enrollment_id: string;
  store_id: number;
  store_name: string;
  business_date: string;
  completed_at?: string;
  status: 'CONFIRMED' | 'IN_SERVICE' | 'COMPLETED' | 'CANCELLED';
  completion_invalidated: boolean;
  agreed_remuneration: number;
  planned_remuneration: number;
  accrued_remuneration: number;
}
export interface SelfRemunerationItem {
  kind: 'COURSE' | 'SPECIAL_SERVICE' | 'EXTENSION' | 'SURCHARGE';
  name: string;
  price: number;
  remuneration: number;
  line_id?: string;
  duration_minutes?: number;
  service_id?: string;
  revision_id?: string;
  revision_number?: number;
  adoption_basis?: 'CURRENT_SETTING' | 'ACCEPTED_TERMS' | 'HISTORICAL_CORRECTION';
  adopted_at?: string;
  charge_type?: 'FREE' | 'PAID';
  terms_version?: number;
  consent_event_id?: string;
  consent_version?: number;
}
export interface SelfRemuneration extends SelfRemunerationSummary {
  version: number;
  items: SelfRemunerationItem[];
}
export interface SelfRemunerationSnapshot {
  items: SelfRemunerationItem[];
  agreed_remuneration: number;
  accrued_remuneration: number;
  completion_invalidated: boolean;
}
export interface SelfRemunerationChange {
  change_id: string;
  change_type: 'CORRECTION' | 'COMPLETION_INVALIDATION';
  order_id: string;
  business_date: string;
  completed_at: string;
  changed_at: string;
  reason: string;
  before_version: number;
  after_version: number;
  before: SelfRemunerationSnapshot;
  after: SelfRemunerationSnapshot;
}
