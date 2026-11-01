import { PageResult, PaginationParams, apiClient, fromSpringPage } from '@/shared/api';
import { requireId } from '@/shared/lib';
import {
  CastCreateRequest,
  CastEnrollmentStatusResponse,
  CastEnrollmentStatusHistory,
  CastEnrollmentSnapshot,
  CastPublicationStatus,
  CastFieldDefinitionCreateRequest,
  CastFieldDefinitionResponse,
  CastFieldDefinitionUpdateRequest,
  CastInvitationIssueResponse,
  CastPublicResponse,
  CastResponse,
  CastSummaryResponse,
  CastUpdateRequest,
} from '../model/types';

export const castApi = {
  suspend: async (id: string): Promise<CastEnrollmentStatusResponse> => {
    const response = await apiClient.post(`/casts/${requireId(id, 'キャスト')}/suspension`);
    return response.data;
  },
  resume: async (id: string): Promise<CastEnrollmentStatusResponse> => {
    const response = await apiClient.post(`/casts/${requireId(id, 'キャスト')}/resumption`);
    return response.data;
  },
  withdraw: async (id: string): Promise<CastEnrollmentStatusResponse> => {
    const response = await apiClient.post(`/casts/${requireId(id, 'キャスト')}/withdrawal`);
    return response.data;
  },
  statusHistories: async (
    id: string,
    page = 0
  ): Promise<PageResult<CastEnrollmentStatusHistory>> => {
    const response = await apiClient.get(`/casts/${requireId(id, 'キャスト')}/status-histories`, {
      params: { page, size: 20 },
    });
    return fromSpringPage(response.data);
  },
  snapshots: async (id: string, page = 0): Promise<PageResult<CastEnrollmentSnapshot>> => {
    const response = await apiClient.get(`/casts/${requireId(id, 'キャスト')}/snapshots`, {
      params: { page, size: 20 },
    });
    return fromSpringPage(response.data);
  },
  changePublication: async (
    id: string,
    publication_status: CastPublicationStatus
  ): Promise<{ publication_status: CastPublicationStatus }> => {
    const response = await apiClient.patch(`/casts/${requireId(id, 'キャスト')}/publication`, {
      publication_status,
    });
    return response.data;
  },
  /** キャスト一覧を取得する */
  list: async (params?: PaginationParams): Promise<PageResult<CastSummaryResponse>> => {
    const response = await apiClient.get('/casts', { params });
    return fromSpringPage(response.data);
  },
  /** キャスト詳細を取得する */
  get: async (id: string | undefined): Promise<CastResponse> => {
    const response = await apiClient.get(`/casts/${requireId(id, 'キャスト')}`);
    return response.data;
  },
  /** キャストを新規作成する */
  create: async (data: CastCreateRequest): Promise<CastResponse> => {
    const response = await apiClient.post('/casts', data);
    return response.data;
  },
  /** キャスト情報を更新する */
  update: async (id: string | undefined, data: CastUpdateRequest): Promise<CastResponse> => {
    const response = await apiClient.put(`/casts/${requireId(id, 'キャスト')}`, data);
    return response.data;
  },
  /** キャストを削除する */
  delete: async (id: string | undefined): Promise<void> => {
    await apiClient.delete(`/casts/${requireId(id, 'キャスト')}`);
  },
  /** 公開キャスト一覧を取得する */
  listPublic: async (): Promise<CastPublicResponse[]> => {
    const response = await apiClient.get('/casts/public');
    return response.data;
  },
  /** キャストへの招待を発行する（店長限定。再発行時は旧招待が失効する） */
  issueInvitation: async (id: string | undefined): Promise<CastInvitationIssueResponse> => {
    const response = await apiClient.post(`/casts/${requireId(id, 'キャスト')}/invitation`);
    return response.data;
  },
};

export const castFieldDefinitionApi = {
  /** カスタムフィールド定義一覧を取得する */
  list: async (): Promise<CastFieldDefinitionResponse[]> => {
    const response = await apiClient.get('/casts/fields');
    return response.data;
  },
  /** カスタムフィールド定義を新規作成する */
  create: async (data: CastFieldDefinitionCreateRequest): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.post('/casts/fields', data);
    return response.data;
  },
  /** カスタムフィールド定義を更新する */
  update: async (
    id: string | undefined,
    data: CastFieldDefinitionUpdateRequest
  ): Promise<CastFieldDefinitionResponse> => {
    const response = await apiClient.put(`/casts/fields/${requireId(id, 'フィールド')}`, data);
    return response.data;
  },
  /** カスタムフィールド定義を削除する */
  delete: async (id: string | undefined): Promise<void> => {
    await apiClient.delete(`/casts/fields/${requireId(id, 'フィールド')}`);
  },
};
