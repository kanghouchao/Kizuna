import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  RoleSummaryResponse,
  ServiceIdentityCreateRequest,
  ServiceIdentityResponse,
  ServiceIdentitySummaryResponse,
  ServiceIdentityUpdateRequest,
} from '../model/types';

/**
 * サービスID管理 API（本人種別 SERVICE の作成・一覧・授権変更・停止・再開）。
 * 全端点 SERVICE_ID_MANAGE 権限限定で、スタッフのアカウント管理とは別の面。
 */
export const serviceIdentityApi = {
  /** 一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形）。検索軸は表示名だけ（email が無い） */
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<ServiceIdentitySummaryResponse>> => {
    const response = await apiClient.get('/platform/service-identities', {
      params: {
        ...toSpringPageParams(params.page, params.size),
        search: params.search,
      },
    });
    return fromSpringPage(response.data);
  },
  /** 1 件取得（version を持つ詳細）。授権編集の開始時と、競合後の最新版の取り直しに使う。 */
  get: async (id: number): Promise<ServiceIdentityResponse> => {
    const response = await apiClient.get(`/platform/service-identities/${id}`);
    return response.data;
  },
  create: async (data: ServiceIdentityCreateRequest): Promise<ServiceIdentityResponse> => {
    const response = await apiClient.post('/platform/service-identities', data);
    return response.data;
  },
  update: async (
    id: number,
    data: ServiceIdentityUpdateRequest
  ): Promise<ServiceIdentityResponse> => {
    const response = await apiClient.put(`/platform/service-identities/${id}`, data);
    return response.data;
  },
  suspend: async (id: number): Promise<void> => {
    await apiClient.post(`/platform/service-identities/${id}/suspension`);
  },
  resume: async (id: number): Promise<void> => {
    await apiClient.post(`/platform/service-identities/${id}/resumption`);
  },
  /** 付与できるロール（自作ロールのみ）。付与可否の述語はサーバ側の単源で、前端では濾さない。 */
  grantableRoles: async (): Promise<RoleSummaryResponse[]> => {
    const response = await apiClient.get('/platform/service-identities/grantable-roles');
    return response.data;
  },
};
