// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）。
// 応答の任意性は Java 側の可空性が正本。wrapper 型のフィールドは
// default-property-inclusion: non_null によりキーごと応答から消えるため optional にする。

// 受注ステータス。CREATED=未確定/CONFIRMED=確定/COMPLETED=完了/CANCELLED=キャンセル。
export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

// 受付経路。WEB=会員ポータルからの申請/PHONE=電話受付。
export type ReceptionRoute = 'WEB' | 'PHONE';

/**
 * 受注ステータスの日本語表示（既定）。
 *
 * CREATED は会員の申請でも店舗が手入力した受注でも起きるため、中立に「未確定」と呼ぶ。
 * 申請かどうかは別途 WEB申請 バッジが担う。
 */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: '未確定',
  CONFIRMED: '確定',
  COMPLETED: '完了',
  CANCELLED: 'キャンセル',
};

/** 会員ポータルでの表示。会員本人の予約は必ず申請として起きるので、CREATED を「申請中」と呼べる。 */
export const MEMBER_ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  ...ORDER_STATUS_LABELS,
  CREATED: '申請中',
};

export interface Order {
  id?: string;
  receptionist_id?: number;
  receptionist_name?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id?: string;
  cast_name?: string;
  pax?: number;
  course_minutes?: number;
  extension_minutes?: number;
  option_codes?: string[];
  discount_name?: string;
  manual_discount?: number;
  carrier?: string;
  media_name?: string;
  used_points?: number;
  manual_grant_points?: number;
  remarks?: string;
  cast_driver_message?: string;
  status?: OrderStatus;
  reception_route?: ReceptionRoute;
  /** 申請した会員の会員コード。店舗が起こした受注では応答から消える。 */
  requester_member_code?: string;
  location_address?: string;
  location_building?: string;
}

export interface OrderReceptionist {
  id?: number;
  display_name?: string;
}

/**
 * 指名候補 1 件（当店に在籍中のキャスト）。
 *
 * キャスト管理の CastResponse とは別物で、ドロップダウンに要る最小限だけを持つ。読み口が受注側にあるのは、
 * 指名が受注の操作で、候補の範囲も要る権限も受注側が決めるため。
 */
export interface OrderCastCandidate {
  id?: string;
  name?: string;
}

export interface OrderCreateRequest {
  // 受付とキャストは Java 側が @NotNull / @NotBlank のため必須
  receptionist_id: number;
  business_date: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id: string;
  pax?: number;
  course_minutes?: number;
  extension_minutes?: number;
  option_codes?: string[];
  discount_name?: string;
  manual_discount?: number;
  reception_route?: ReceptionRoute;
  carrier?: string;
  media_name?: string;
  used_points?: number;
  manual_grant_points?: number;
  remarks?: string;
  cast_driver_message?: string;
  // Customer Creation Fields
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  landmark?: string;
  has_pet?: boolean;
  ng_type?: string;
  ng_content?: string;
}

// 未確定の予約申請に対する店舗側の編集（PUT /store/orders/reservation-requests/{id}）。
// 送った内容がそのまま新しい申請内容になる部分更新ではない契約で、省略した項目は未設定になる。
// 指名・受付担当を外せることがこの契約の目的なので、両者は可空。
export interface ReservationRequestUpdateRequest {
  receptionist_id?: number;
  cast_id?: string;
  // Java 側が @NotNull @Min(1)
  pax: number;
  remarks?: string;
}

// 会員本人の予約1件（GET /platform/me/orders）。店舗の顧客台帳の項目は含まない。
export interface MemberOrder {
  id?: string;
  store_id?: number;
  store_name?: string;
  business_date?: string;
  arrival_scheduled_start_time?: string;
  pax?: number;
  cast_name?: string;
  status?: OrderStatus;
}

// 会員本人の予約申請（POST /platform/me/orders）。受付担当・顧客・受付経路はサーバ側が決める。
export interface MemberOrderCreateRequest {
  store_id: number;
  business_date: string;
  pax: number;
  arrival_scheduled_start_time?: string;
  cast_id?: string;
  remarks?: string;
}
