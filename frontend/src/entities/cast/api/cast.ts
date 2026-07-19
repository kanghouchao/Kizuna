import { Page, PaginationParams, apiClient } from '@/shared/api';
import {
  CastCreateRequest,
  CastFieldDefinitionCreateRequest,
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  CastInvitationIssueResponse,
  CastPublicResponse,
  CastResponse,
  CastUpdateRequest,
} from '../model/types';

export const castApi = {
  /** キャスト一覧を取得する */
  list: async (params?: PaginationParams): Promise<Page<CastResponse>> => {
    const response = await apiClient.get('/store/casts', { params });
    return response.data;
  },
  /** キャスト詳細を取得する */
  get: async (id: string): Promise<CastResponse> => {
    const response = await apiClient.get(`/store/casts/${id}`);
    return response.data;
  },
  /** キャストを新規作成する */
  create: async (data: CastCreateRequest): Promise<CastResponse> => {
    const response = await apiClient.post('/store/casts', data);
    return response.data;
  },
  /** キャスト情報を更新する */
  update: async (id: string, data: CastUpdateRequest): Promise<CastResponse> => {
    const response = await apiClient.put(`/store/casts/${id}`, data);
    return response.data;
  },
  /** キャストを削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/casts/${id}`);
  },
  /** 公開キャスト一覧を取得する */
  listPublic: async (): Promise<CastPublicResponse[]> => {
    const response = await apiClient.get('/store/casts/public');
    return response.data;
  },
  /** キャストへの招待を発行する（店長限定。再発行時は旧招待が失効する） */
  issueInvitation: async (id: string): Promise<CastInvitationIssueResponse> => {
    const response = await apiClient.post(`/store/casts/${id}/invitation`);
    return response.data;
  },
};

export const castFieldDefinitionApi = {
  /** カスタムフィールド定義一覧を取得する */
  list: async (): Promise<CastFieldDefinitionResponse[]> => {
    const response = await apiClient.get('/store/casts/fields');
    return response.data;
  },
  /** カスタムフィールド定義を新規作成する */
  create: async (data: CastFieldDefinitionCreateRequest): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.post('/store/casts/fields', data);
    return response.data;
  },
  /** カスタムフィールド定義を更新する */
  update: async (
    id: string,
    data: CastFieldDefinitionUpdateRequest
  ): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.put(`/store/casts/fields/${id}`, data);
    return response.data;
  },
  /** カスタムフィールド定義を削除する */
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/store/casts/fields/${id}`);
  },
};
