import { CastEnrollmentStatus } from './types';

export interface AdminCastSummaryResponse {
  id: number;
  display_name: string;
  real_name: string | null;
}

export interface AdminCastResponse extends AdminCastSummaryResponse {
  user_id: number;
  birth_date: string | null;
}

export interface AdminCastEnrollmentResponse {
  id: string;
  store_id: number;
  store_name: string;
  name: string;
  status: CastEnrollmentStatus;
  started_on: string;
  ended_at: string | null;
}
