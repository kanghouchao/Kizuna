import { PageResult, PaginationParams, apiClient, fromSpringPage } from '@/shared/api';
import {
  CustomerCreateRequest,
  CustomerMemberLinkHistoryResponse,
  CustomerMemberLinkResponse,
  CustomerResponse,
  CustomerUpdateRequest,
} from '../model/types';

// 一覧のクエリ: 共通ページネーション + rank / classification の絞り込み
export type CustomerListParams = PaginationParams & {
  rank?: string;
  classification?: string;
};

export const customerApi = {
  /** 顧客一覧を取得する */
  list: async (params?: CustomerListParams): Promise<PageResult<CustomerResponse>> => {
    const response = await apiClient.get('/store/customers', { params });
    return fromSpringPage(response.data);
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
  /** 会員コードで会員を顧客へ紐づける（既に別の会員と紐づいていれば付け替える） */
  linkMember: async (id: string, memberCode: string): Promise<CustomerMemberLinkResponse> => {
    const response = await apiClient.post(`/store/customers/${id}/member-link`, {
      member_code: memberCode,
    });
    return response.data;
  },
  /** 会員の紐づけを解除する（履歴は残る） */
  unlinkMember: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/customers/${id}/member-link`);
  },
  /** 会員紐づけの履歴を新しい順に取得する */
  memberLinkHistory: async (id: string): Promise<CustomerMemberLinkHistoryResponse[]> => {
    const response = await apiClient.get(`/store/customers/${id}/member-link/history`);
    return response.data;
  },
};
