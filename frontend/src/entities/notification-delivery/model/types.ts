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
/** 下書きの内容。キーを持たない理由は `SendOperation`（shared/lib/hooks/useOperation）。 */
export interface DeliveryInput {
  source_type: 'ORDER' | 'APPLICATION';
  source_id: string;
  channel: 'EMAIL';
  purpose: 'BUSINESS';
  subject: string;
  body: string;
  scheduled_at: string;
}
export interface DeliveryAttempt {
  id: string;
  attempt_number: number;
  status: DeliveryStatus;
  failure_code?: string;
  started_at: string;
  finished_at?: string;
  /** 職員の判定（failure_code が MANUAL_RESOLUTION）の試行は定期処理の実行を持たない */
  task_execution_id?: number;
  reason: string;
}
/** 結果不明の通知に職員が下す判定 */
export type DeliveryResolution = 'SENT' | 'FAILED';
