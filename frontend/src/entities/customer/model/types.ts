// 顧客（Customer）レスポンス
export interface CustomerResponse {
  id: string;
  name: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  points?: number;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
}

// 顧客作成リクエスト
export interface CustomerCreateRequest {
  name: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
}

// 顧客更新リクエスト
export interface CustomerUpdateRequest {
  name?: string;
  phone_number?: string;
  phone_number2?: string;
  address?: string;
  building_name?: string;
  classification?: string;
  has_pet?: boolean;
  rank?: string;
  line_id?: string;
  usage_areas?: string;
  ng_type?: string;
  ng_content?: string;
}
