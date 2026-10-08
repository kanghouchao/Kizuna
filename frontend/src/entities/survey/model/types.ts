export type RevisionStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
export type AnswerStatus = 'ACTIVE' | 'WITHDRAWN';
export type ReceivedVia = 'PAPER' | 'VERBAL' | 'EXISTING_RECORD';
export interface Actor {
  id: string;
  display_name: string;
}
export interface Question {
  question_key: string;
  type: 'TEXT' | 'SINGLE_CHOICE';
  prompt: string;
  required: boolean;
  options: { option_key: string; label: string }[];
}
export interface DefinitionInput {
  title: string;
  questions: Question[];
}
export interface SurveySummary {
  id: string;
  latest_revision_id: string;
  latest_revision_number: number;
  latest_title: string;
  latest_status: RevisionStatus;
  open_revision_id?: string;
  draft_revision_id?: string;
  created_at: string;
}
export interface Survey extends SurveySummary {
  created_by: Actor;
  retention_policy: 'NOT_CONFIGURED';
}
export interface RevisionSummary {
  id: string;
  survey_id: string;
  revision_number: number;
  title: string;
  status: RevisionStatus;
  version: number;
  created_at: string;
  opened_at?: string;
  closed_at?: string;
}
export interface Revision extends RevisionSummary {
  based_on_revision_id?: string;
  questions: Question[];
  created_by: Actor;
  retention_policy: 'NOT_CONFIGURED';
}
export interface AnswerValue {
  question_key: string;
  text?: string;
  option_key?: string;
}
export interface AnswerSummary {
  id: string;
  survey_id: string;
  revision_id: string;
  revision_number: number;
  intake_source: 'STAFF_RECORDED';
  received_via: ReceivedVia;
  received_at: string;
  created_at: string;
  status: AnswerStatus;
  version: number;
}
export interface Answer extends AnswerSummary {
  answers: AnswerValue[];
  recorded_by: Actor;
  supersedes_id?: string;
  superseded_by_id?: string;
  retention_policy: 'NOT_CONFIGURED';
}
export interface Counts {
  survey_id: string;
  revision_id: string;
  total_records: number;
  active_records: number;
  withdrawn_records: number;
}
export type Operation =
  | 'DRAFT_CREATED'
  | 'DRAFT_REPLACED'
  | 'OPENED'
  | 'CLOSED'
  | 'RECEIVED'
  | 'WITHDRAWN'
  | 'CORRECTION_RECEIVED'
  | 'CORRECTION_LINKED';
export interface SurveyHistory {
  id: string;
  type: Operation;
  created_at: string;
  actor: Actor;
  before_version?: number;
  after_version: number;
  before_status?: string;
  after_status: string;
  reason?: string;
  related_response_id?: string;
}
export interface Receipt {
  id: string;
  type: Operation;
  resource_id: string;
  committed_version: number;
  replayed: boolean;
}
export type WriteResponse =
  { revision: Revision; operation: Receipt } | { answer: Answer; operation: Receipt };
export interface SurveySearch {
  q?: string;
  survey_id?: string;
  latest_status?: RevisionStatus;
  sort?: 'CREATED_DESC' | 'CREATED_ASC';
}
export interface AnswerSearch {
  response_id?: string;
  status?: AnswerStatus;
  sort?: 'RECEIVED_DESC' | 'RECEIVED_ASC' | 'CREATED_DESC' | 'CREATED_ASC';
}
export interface AnswerInput {
  received_via: ReceivedVia;
  received_at: string;
  answers: AnswerValue[];
  dedupe_key: string;
}
export interface ActionInput {
  version: number;
  reason: string;
  dedupe_key: string;
}
export type SurveyCommand =
  | { kind: 'CREATE'; input: DefinitionInput & { dedupe_key: string } }
  | {
      kind: 'REVISE';
      sid: string;
      input: DefinitionInput & { based_on_revision_id: string; dedupe_key: string };
    }
  | {
      kind: 'REPLACE';
      sid: string;
      rid: string;
      input: DefinitionInput & { version: number; dedupe_key: string };
    }
  | { kind: 'OPEN' | 'CLOSE'; sid: string; rid: string; input: ActionInput }
  | { kind: 'RECEIVE'; sid: string; rid: string; input: AnswerInput & { revision_version: number } }
  | { kind: 'CORRECT'; aid: string; input: AnswerInput & ActionInput }
  | { kind: 'WITHDRAW'; aid: string; input: ActionInput };
