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
