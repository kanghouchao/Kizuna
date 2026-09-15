export type ServiceKind = 'COURSE' | 'SPECIAL_SERVICE' | 'SURCHARGE';
export type ChargeType = 'PAID' | 'FREE';
export interface ServiceCreateRequest {
  kind: ServiceKind;
  name: string;
  duration_minutes?: number;
  charge_type?: ChargeType;
  price: number;
  remuneration: number;
}
export type ServiceUpdateRequest = Omit<ServiceCreateRequest, 'kind'> & {
  expected_version: number;
};
export interface ServiceSummary extends ServiceCreateRequest {
  id: string;
  version: number;
  deleted: boolean;
}
export interface ServiceResponse extends ServiceSummary {
  created_at: string;
  updated_at: string;
}
export interface ServiceRevisionResponse {
  id: string;
  version: number;
  operation: 'CREATED' | 'UPDATED' | 'DELETED';
  actor_id: string;
  occurred_at: string;
  before?: ServiceSummary;
  after: ServiceSummary;
}
export const serviceKindLabels: Record<ServiceKind, string> = {
  COURSE: 'コース',
  SPECIAL_SERVICE: '特殊サービス',
  SURCHARGE: '加算',
};
