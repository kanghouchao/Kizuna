export interface ExecutionSummary {
  id: number;
  request_id: number;
  attempt_number: number;
  task_name: string;
  service_user_id: number;
  service_name: string;
  period_start: string;
  period_end: string;
  origin: string;
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'INTERRUPTED';
  started_at: string;
  finished_at: string | null;
  processed_count: number | null;
  failure_code: string | null;
}
export interface ExecutionResponse {
  execution: ExecutionSummary;
  logical_key: string;
  retry_of: number | null;
  initiated_by: number | null;
  reason: string;
}
export interface ServiceCandidate {
  id: number;
  display_name: string;
}
export interface ExecutionRequest {
  task_name: string;
  logical_key: string;
  service_user_id: number;
  period_start: string;
  period_end: string;
}

export interface TaskType {
  name: string;
  manual_allowed: boolean;
}
