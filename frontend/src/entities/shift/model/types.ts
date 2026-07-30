// シフト（Shift）レスポンス。時刻は ISO 文字列（work_date=yyyy-MM-dd / start_time・end_time=HH:mm:ss）。
export interface ShiftResponse {
  id?: string;
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
  public_visible?: boolean;
  approved_by?: string;
  approved_at?: string;
  attendance_confirmed?: boolean;
  actual_start_time?: string;
  actual_end_time?: string;
  actual_recorded_by?: string;
  actual_recorded_at?: string;
  created_at?: string;
  updated_at?: string;
}

// シフト作成リクエスト（status 省略時はサーバ側で TENTATIVE）
export interface ShiftCreateRequest {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status?: string;
  public_visible?: boolean;
}

// シフト更新リクエスト
export interface ShiftUpdateRequest {
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
  public_visible?: boolean;
}

// 本人（キャスト）ポータル週間スケジュールの1件（GET /platform/me/schedule）。店舗名を内联する。
export interface CastScheduleItem {
  id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
  attendance_confirmed?: boolean;
  actual_start_time?: string;
  actual_end_time?: string;
  actual_recorded_by?: string;
  actual_recorded_at?: string;
  store_id?: number;
  store_name?: string;
}

// 出勤希望のステータス。PENDING=受付済み/APPROVED=確定済み/DECLINED=却下。
export type ShiftRequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED';
export type ShiftRequestKind = 'NEW' | 'CHANGE';

// 出勤希望の提出リクエスト（本人・cast）。work_date は 'yyyy-MM-dd'、時刻は 'HH:mm:ss'。
export interface ShiftRequestCreateRequest {
  store_id: number;
  work_date: string;
  start_time: string;
  end_time: string;
  note?: string;
}

export interface ShiftChangeRequestCreateRequest {
  target_shift_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note?: string;
}

export interface ShiftActualRequest {
  start_time: string;
  end_time: string;
}

// 出勤希望提出の応答（本人ポータル）。
export interface ShiftRequestResponse {
  id?: string;
  store_id?: number;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  // 未入力はキーごと応答から消えるため undefined であって null ではない
  note?: string;
  status?: ShiftRequestStatus;
  kind?: ShiftRequestKind;
  target_shift_id?: string;
  decided_by?: string;
  decided_at?: string;
  created_at?: string;
}

// 本人（キャスト）の出勤希望履歴の1件（GET /platform/me/shift-requests）。店舗名を埋め込む。
export interface CastShiftRequestItem {
  id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  note?: string;
  status?: ShiftRequestStatus;
  kind?: ShiftRequestKind;
  target_shift_id?: string;
  decided_by?: string;
  decided_at?: string;
  store_id?: number;
  store_name?: string;
  created_at?: string;
}

// 本人（キャスト）所属店舗セレクタの1件（GET /platform/me/stores）。
export interface CastStoreItem {
  store_id?: number;
  store_name?: string;
}

// 店舗側 inbox の出勤希望1件（GET /store/shift-requests）。
export interface StoreShiftRequestItem {
  id?: string;
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  note?: string;
  status?: ShiftRequestStatus;
  kind?: ShiftRequestKind;
  target_shift_id?: string;
  decided_by?: string;
  decided_at?: string;
  current_work_date?: string;
  current_start_time?: string;
  current_end_time?: string;
}
