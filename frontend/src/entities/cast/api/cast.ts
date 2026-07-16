import { Page, PaginationParams, apiClient } from '@/shared/api';
import {
  CastCreateRequest,
  CastFieldDefinitionCreateRequest,
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  CastInvitationIssueResponse,
  CastResponse,
  CastUpdateRequest,
} from '../model/types';

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
  /** キャストへの招待を発行する（店長限定。再発行時は旧招待が失効する） */
  issueInvitation: async (id: string): Promise<CastInvitationIssueResponse> => {
    const response = await apiClient.post(`/tenant/casts/${id}/invitation`);
    return response.data;
  },
};

export const castFieldDefinitionApi = {
  /** カスタムフィールド定義一覧を取得する */
  list: async (): Promise<CastFieldDefinitionResponse[]> => {
    const response = await apiClient.get('/tenant/casts/fields');
    return response.data;
  },
  /** カスタムフィールド定義を新規作成する */
  create: async (data: CastFieldDefinitionCreateRequest): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.post('/tenant/casts/fields', data);
    return response.data;
  },
  /** カスタムフィールド定義を更新する */
  update: async (
    id: string,
    data: CastFieldDefinitionUpdateRequest
  ): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.put(`/tenant/casts/fields/${id}`, data);
    return response.data;
  },
  /** カスタムフィールド定義を削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/tenant/casts/fields/${id}`);
  },
};
