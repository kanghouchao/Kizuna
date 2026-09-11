import { CastEnrollmentStatus } from './types';

export interface PlatformCastSummaryResponse {
  id: number;
  display_name: string;
  real_name: string | null;
}

export interface PlatformCastResponse extends PlatformCastSummaryResponse {
  platform_user_id: number;
  birth_date: string | null;
}

export interface PlatformCastEnrollmentResponse {
  id: string;
  store_id: number;
  store_name: string;
  name: string;
  status: CastEnrollmentStatus;
  ended_at: string | null;
}
