import { Page, PaginationParams, apiClient } from '@/shared/api';
import { CustomerCreateRequest, CustomerResponse, CustomerUpdateRequest } from '../model/types';

// 一覧のクエリ: 共通ページネーション + rank / classification の絞り込み
export type CustomerListParams = PaginationParams & {
  rank?: string;
  classification?: string;
};

export const customerApi = {
  /** 顧客一覧を取得する */
  list: async (params?: CustomerListParams): Promise<Page<CustomerResponse>> => {
    const response = await apiClient.get('/store/customers', { params });
    return response.data;
  },
  /** 顧客詳細を取得する */
  get: async (id: string): Promise<CustomerResponse> => {
    const response = await apiClient.get(`/store/customers/${id}`);
    return response.data;
  },
  /** 顧客を新規作成する */
  create: async (data: CustomerCreateRequest): Promise<CustomerResponse> => {
    const response = await apiClient.post('/store/customers', data);
    return response.data;
  },
  /** 顧客情報を更新する */
  update: async (id: string, data: CustomerUpdateRequest): Promise<CustomerResponse> => {
    const response = await apiClient.put(`/store/customers/${id}`, data);
    return response.data;
  },
  /** 顧客を削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/customers/${id}`);
  },
};
