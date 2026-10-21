import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import { requireId } from '@/shared/lib';
import {
  CreateStoreRequest,
  CreateStoreResponse,
  Store,
  StoreStats,
  UpdateStoreRequest,
} from '../model/types';

export const platformStoreApi = {
  /** 店舗一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形） */
  getList: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<Store>> => {
    const response = await apiClient.get('/platform/stores', {
      params: { ...toSpringPageParams(params.page, params.size), search: params.search },
    });
    return fromSpringPage(response.data);
  },
  /** ドメインから店舗を引く（公開・匿名）。ブラウザから渡されたドメインをサーバ側の正本に突き合わせる用途。 */
  lookupByDomain: async (domain: string): Promise<Store> => {
    const response = await apiClient.get('/platform/stores/lookup', { params: { domain } });
    return response.data;
  },
  getById: async (id: string | undefined): Promise<Store> => {
    const response = await apiClient.get(`/platform/stores/${requireId(id, '店舗')}`);
    return response.data;
  },
  /** 端点は 201 Created。body には作成された店舗の id だけが載る。 */
  create: async (data: CreateStoreRequest): Promise<CreateStoreResponse> => {
    const response = await apiClient.post('/platform/stores', data);
    return response.data;
  },
  /** 端点は 204 No Content。body は返らない。 */
  update: async (id: string | undefined, data: UpdateStoreRequest): Promise<void> => {
    await apiClient.put(`/platform/stores/${requireId(id, '店舗')}`, data);
  },
  delete: async (id: string | undefined): Promise<void> => {
    await apiClient.delete(`/platform/stores/${requireId(id, '店舗')}`);
  },
  getStats: async (): Promise<StoreStats> => {
    const response = await apiClient.get('/platform/stores/stats');
    return response.data;
  },
};
