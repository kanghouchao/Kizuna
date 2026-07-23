// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）
export interface Order {
  id: string;
  store_name: string;
  receptionist_id?: string;
  receptionist_name?: string;
  business_date: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id?: string;
  cast_name?: string;
  course_minutes: number;
  extension_minutes: number;
  option_codes: string[];
  discount_name?: string;
  manual_discount: number;
  carrier?: string;
  media_name?: string;
  used_points: number;
  manual_grant_points: number;
  remarks?: string;
  cast_driver_message?: string;
  status: string;
}

export interface OrderCreateRequest {
  store_name: string;
  receptionist_id?: string;
  business_date: string;
  arrival_scheduled_start_time?: string;
  arrival_scheduled_end_time?: string;
  customer_id?: string;
  customer_name?: string;
  cast_id?: string;
  course_minutes: number;
  extension_minutes: number;
  option_codes: string[];
  discount_name?: string;
  manual_discount: number;
  carrier?: string;
  media_name?: string;
  used_points: number;
  manual_grant_points: number;
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
