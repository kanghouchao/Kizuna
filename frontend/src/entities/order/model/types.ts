// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）。
// 応答の任意性は Java 側の可空性が正本。wrapper 型のフィールドは
// default-property-inclusion: non_null によりキーごと応答から消えるため optional にする。
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
  status?: string;
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
