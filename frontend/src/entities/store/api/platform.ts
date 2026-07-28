import { apiClient, PageResult, fromLaravelPage, toLaravelPageParams } from '@/shared/api';
import { CreateStoreRequest, Store, StoreStats, UpdateStoreRequest } from '../model/types';

export const platformStoreApi = {
  /** 店舗一覧を取得する。page は他一覧と同じ 0 起点で、1 起点のワイヤ形式への換算はここに閉じる */
  getList: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<Store>> => {
    const response = await apiClient.get('/platform/stores', {
      params: { ...toLaravelPageParams(params.page, params.size), search: params.search },
    });
    return fromLaravelPage(response.data);
  },
  getById: async (id: string): Promise<Store> => {
    const response = await apiClient.get(`/platform/stores/${id}`);
    return response.data;
  },
  create: async (data: CreateStoreRequest): Promise<Store> => {
    const response = await apiClient.post('/platform/stores', data);
    return response.data;
  },
  update: async (id: string, data: UpdateStoreRequest): Promise<Store> => {
    const response = await apiClient.put(`/platform/stores/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/platform/stores/${id}`);
  },
  getStats: async (): Promise<StoreStats> => {
    const response = await apiClient.get('/platform/stores/stats');
    return response.data;
  },
};
