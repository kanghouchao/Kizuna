// シフト（Shift）レスポンス。時刻は ISO 文字列（work_date=yyyy-MM-dd / start_time・end_time=HH:mm:ss）。
export interface ShiftResponse {
  id: string;
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status: string;
  created_at: string;
  updated_at: string;
}

// シフト作成リクエスト（status 省略時はサーバ側で TENTATIVE）
export interface ShiftCreateRequest {
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  status?: string;
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
  work_date: string;
  start_time: string;
  end_time: string;
  status: string;
  store_id: number;
  store_name: string;
}

// 出勤希望のステータス。PENDING=受付済み/APPROVED=確定済み/DECLINED=却下。
export type ShiftRequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED';

// 出勤希望の提出リクエスト（本人・cast）。work_date は 'yyyy-MM-dd'、時刻は 'HH:mm:ss'。
export interface ShiftRequestCreateRequest {
  store_id: number;
  work_date: string;
  start_time: string;
  end_time: string;
  note?: string;
}

// 出勤希望提出の応答（本人ポータル）。
export interface ShiftRequestResponse {
  id: string;
  store_id: number;
  work_date: string;
  start_time: string;
  end_time: string;
  note: string | null;
  status: ShiftRequestStatus;
  created_at: string;
}

// 本人（キャスト）の出勤希望履歴の1件（GET /platform/me/shift-requests）。店舗名を内联する。
export interface CastShiftRequestItem {
  id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note: string | null;
  status: ShiftRequestStatus;
  store_id: number;
  store_name: string;
  created_at: string;
}

// 本人（キャスト）所属店舗セレクタの1件（GET /platform/me/stores）。
export interface CastStoreItem {
  store_id: number;
  store_name: string;
}

// 店舗側 inbox の出勤希望1件（GET /store/shift-requests）。
export interface StoreShiftRequestItem {
  id: string;
  cast_id: string;
  work_date: string;
  start_time: string;
  end_time: string;
  note: string | null;
  status: ShiftRequestStatus;
}
