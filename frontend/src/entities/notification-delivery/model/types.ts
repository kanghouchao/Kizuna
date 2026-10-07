export type DeliveryStatus =
  'DRAFT' | 'QUEUED' | 'DISPATCHED' | 'SENDING' | 'SENT' | 'FAILED' | 'BLOCKED' | 'UNKNOWN';
export interface DeliverySummary {
  id: string;
  source_type: 'ORDER' | 'APPLICATION';
  source_id: string;
  channel: 'EMAIL';
  purpose: 'BUSINESS';
  scheduled_at: string;
  status: DeliveryStatus;
  attempt_count: number;
  created_at: string;
  version: number;
}
export interface Delivery extends DeliverySummary {
  subject: string;
  body: string;
  dedupe_key: string;
  contact_decision: 'ALLOWED' | 'STORE_DENIED' | 'NOT_ALLOWED';
  transport_availability: 'AVAILABLE' | 'UNAVAILABLE';
  scheduling_availability: 'MANUAL_TASK_ONLY';
}
export interface DeliveryInput {
  source_type: 'ORDER' | 'APPLICATION';
  source_id: string;
  channel: 'EMAIL';
  purpose: 'BUSINESS';
  subject: string;
  body: string;
  scheduled_at: string;
  dedupe_key: string;
}
export interface DeliveryAttempt {
  id: string;
  attempt_number: number;
  status: DeliveryStatus;
  failure_code?: string;
  started_at: string;
  finished_at?: string;
  task_execution_id: number;
  reason: string;
}
