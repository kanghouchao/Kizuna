export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type PermissionStatus = 'NOT_GRANTED' | 'GRANTED' | 'REVOKED';
export type ReceivedVia = 'PAPER' | 'VERBAL' | 'ELECTRONIC';
export type Basis = 'WRITTEN' | 'VERBAL' | 'ELECTRONIC_RECORD';
export interface Actor {
  id: string;
  display_name: string;
}
export interface ReviewSummary {
  id: string;
  intake_source: 'STAFF_RECORDED';
  display_name: string | null;
  received_via: ReceivedVia;
  received_at: string;
  created_at: string;
  status: ReviewStatus;
  permission_status: PermissionStatus;
  version: number;
}
export interface ReviewPermission {
  id: string;
  scope: 'OWN_STORE_WEBSITE';
  basis_type: Basis;
  granted_at: string;
  evidence_note: string;
  recorded_by: Actor;
  recorded_at: string;
  revocation: {
    withdrawal_received_at: string;
    reason: string;
    recorded_by: Actor;
    recorded_at: string;
  } | null;
}
export interface Review extends ReviewSummary {
  body: string;
  origin_order_id: string | null;
  origin_checked_at: string | null;
  origin_order_version: number | null;
  recorded_by: Actor;
  supersedes_id: string | null;
  superseded_by_id: string | null;
  permission: ReviewPermission | null;
  publication_eligible: boolean;
  publication_blockers: ('NOT_APPROVED' | 'WITHDRAWN' | 'NO_PERMISSION' | 'PERMISSION_REVOKED')[];
  publication_connection: 'NOT_CONFIGURED';
}
export type Operation =
  | 'RECEIVED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'PERMISSION_GRANTED'
  | 'PERMISSION_REVOKED'
  | 'CORRECTION_RECEIVED';
export interface ReviewHistory {
  id: string;
  type: Operation | 'CORRECTION_LINKED';
  created_at: string;
  actor: Actor;
  before_version: number | null;
  after_version: number;
  before_status: ReviewStatus | null;
  after_status: ReviewStatus;
  before_permission_status: PermissionStatus | null;
  after_permission_status: PermissionStatus;
  reason: string | null;
  related_review_id: string | null;
  permission_record_id: string | null;
}
export interface ReviewWriteResponse {
  review: Review;
  operation: {
    id: string;
    type: Operation;
    review_id: string;
    committed_version: number;
    replayed: boolean;
  };
}
export interface ReviewInput {
  body: string;
  display_name: string | null;
  received_via: ReceivedVia;
  received_at: string;
  origin_order_id: string | null;
  dedupe_key: string;
}
export type ActionInput = { version: number; reason: string; dedupe_key: string };
export type ReviewCommand =
  | { kind: 'CREATE'; input: ReviewInput }
  | { kind: 'CORRECT'; id: string; input: ReviewInput & ActionInput }
  | { kind: 'DECIDE'; id: string; input: ActionInput & { decision: 'APPROVE' | 'REJECT' } }
  | { kind: 'WITHDRAW'; id: string; input: ActionInput }
  | {
      kind: 'GRANT';
      id: string;
      input: {
        version: number;
        basis_type: Basis;
        granted_at: string;
        evidence_note: string;
        dedupe_key: string;
      };
    }
  | { kind: 'REVOKE'; id: string; input: ActionInput & { withdrawal_received_at: string } };
export interface ReviewSearch {
  q?: string;
  review_id?: string;
  status?: ReviewStatus;
  permission_status?: PermissionStatus;
  sort?: 'RECEIVED_DESC' | 'RECEIVED_ASC' | 'CREATED_DESC' | 'CREATED_ASC';
}
