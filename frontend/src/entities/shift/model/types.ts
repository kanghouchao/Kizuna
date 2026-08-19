// シフト（Shift）レスポンス。時刻は ISO 文字列（work_date=yyyy-MM-dd / start_time・end_time=HH:mm:ss）。
// published は店外への露出可否。
export interface ShiftResponse {
  id?: string;
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
  published?: boolean;
  created_at?: string;
  updated_at?: string;
}

// シフト作成リクエスト（status 省略時はサーバ側で TENTATIVE、published 省略時は公開可）
export interface ShiftCreateRequest {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status?: string;
  published?: boolean;
}

// シフト更新リクエスト
export interface ShiftUpdateRequest {
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
}

// 本人（キャスト）ポータル週間スケジュールの1件（GET /platform/me/schedule）。店舗名を内联する。
export interface CastScheduleItem {
  id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  status?: string;
  store_id?: number;
  store_name?: string;
}

// 出勤希望のステータス。PENDING=受付済み/APPROVED=確定済み/DECLINED=却下。
export type ShiftRequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED';

// 出勤希望の種別。NEW=新規希望/CHANGE=確定シフトへの変更申請。
export type ShiftRequestType = 'NEW' | 'CHANGE';

// 出勤希望の提出リクエスト（本人・cast）。work_date は 'yyyy-MM-dd'、時刻は 'HH:mm:ss'。
export interface ShiftRequestCreateRequest {
  store_id: number;
  work_date: string;
  start_time: string;
  end_time: string;
  note?: string;
}

// 確定シフトへの変更申請の提出リクエスト（本人・cast）。対象は shift_id で指定し、店舗はサーバ側でシフトから導出する。
export interface ShiftChangeRequestCreateRequest {
  shift_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note?: string;
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
  type?: ShiftRequestType;
  shift_id?: string;
  status?: ShiftRequestStatus;
  created_at?: string;
}

// 本人（キャスト）の出勤希望履歴の1件（GET /platform/me/shift-requests）。店舗名を埋め込む。
export interface CastShiftRequestItem {
  id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  note?: string;
  type?: ShiftRequestType;
  status?: ShiftRequestStatus;
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
// 変更申請（type=CHANGE）は対象シフトの現行日時（current_*）を内联する（NEW では null）。
// 承認可否（approvable）は両種別に付く。
export interface StoreShiftRequestItem {
  id?: string;
  cast_id?: string;
  work_date?: string;
  start_time?: string;
  end_time?: string;
  note?: string;
  type?: ShiftRequestType;
  shift_id?: string;
  status?: ShiftRequestStatus;
  current_work_date?: string;
  current_start_time?: string;
  current_end_time?: string;
  approvable?: boolean;
}

// 会員の指名候補となる確定シフトのキャスト1件（GET /platform/shifts/casts）。
export interface ConfirmedShiftCast {
  cast_id?: string;
  cast_name?: string;
  cast_photo_url?: string;
  start_time?: string;
  end_time?: string;
}

// 当日実績（Attendance）。実際に出勤した事実の一等記録で、シフト（予定）とは別の集約（ADR 0014）。
// 時刻は暦日付きの 'yyyy-MM-ddTHH:mm:ss'、business_date は帰属営業日 'yyyy-MM-dd'。
// 取消済みの行はどの読み口にも現れないため、取消の標記は無い。
export interface AttendanceResponse {
  id?: string;
  cast_id?: string;
  business_date?: string;
  actual_start_at?: string;
  // 未終了（閉店時にまとめて記入する運用）では載らない
  actual_end_at?: string;
  // 載らないときは飛び込み出勤（予定なし）
  shift_id?: string;
  waiting_place?: string;
  created_at?: string;
  updated_at?: string;
}

// 実績の記録リクエスト。帰属営業日は載せない — シフトからの継承か実開始時刻からの判定でサーバが決める。
export interface AttendanceCreateRequest {
  cast_id: string;
  // 省略・null は飛び込み出勤の記録になる
  shift_id?: string | null;
  actual_start_at: string;
  actual_end_at?: string | null;
  waiting_place?: string | null;
}

// 実績の訂正リクエスト。訂正できる項目の全量を毎回送る（省略は「変更しない」ではなく「値なし」）。
// キャストとシフトの付け替えは載らない — 逃げ道は取消 → 再記録に限る（ADR 0014）。
export interface AttendanceCorrectionRequest {
  business_date: string;
  actual_start_at: string;
  actual_end_at?: string | null;
  waiting_place?: string | null;
}

// 導出された欠勤（確定シフトあり・未取消の実績なし）。行は建たないので識別子も更新時刻も持たない。
export interface AbsenceResponse {
  cast_id?: string;
  business_date?: string;
}
