import { Page, PaginationParams, apiClient } from '@/shared/api';
import { CastCreateRequest, CastResponse, CastUpdateRequest } from '../model/types';

export const castApi = {
  /** キャスト一覧を取得する */
  list: async (params?: PaginationParams): Promise<Page<CastResponse>> => {
    const response = await apiClient.get('/tenant/casts', { params });
    return response.data;
  },
  /** キャスト詳細を取得する */
  get: async (id: string): Promise<CastResponse> => {
    const response = await apiClient.get(`/tenant/casts/${id}`);
    return response.data;
  },
  /** キャストを新規作成する */
  create: async (data: CastCreateRequest): Promise<CastResponse> => {
    const response = await apiClient.post('/tenant/casts', data);
    return response.data;
  },
  /** キャスト情報を更新する */
  update: async (id: string, data: CastUpdateRequest): Promise<CastResponse> => {
    const response = await apiClient.put(`/tenant/casts/${id}`, data);
    return response.data;
  },
  /** キャストを削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/tenant/casts/${id}`);
  },
  /** 公開キャスト一覧を取得する */
  listPublic: async (): Promise<CastResponse[]> => {
    const response = await apiClient.get('/tenant/casts/public');
    return response.data;
  },
};
