// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）。
// 応答の任意性は Java 側の可空性が正本。wrapper 型のフィールドは
// default-property-inclusion: non_null によりキーごと応答から消えるため optional にする。

// 受注ステータス。CREATED=申請中/CONFIRMED=確定/COMPLETED=完了/CANCELLED=キャンセル。
export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

// 受付経路。WEB=会員ポータルからの申請/PHONE=電話受付。
export type ReceptionRoute = 'WEB' | 'PHONE';

/** 受注ステータスの日本語表示。 */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: '申請中',
  CONFIRMED: '確定',
  COMPLETED: '完了',
  CANCELLED: 'キャンセル',
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
