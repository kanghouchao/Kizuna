export interface AuditEventSummary {
  id: number;
  occurred_at: string;
  actor_id: number;
  actor_type: string;
  actor_name: string;
  store_id: number | null;
  action: string;
  result: string;
  target_type: string;
  target_id: string;
  source_type: string | null;
  source_id: string | null;
}
export interface AuditEventResponse {
  event: AuditEventSummary;
  before_values: Record<string, string>;
  after_values: Record<string, string>;
}
