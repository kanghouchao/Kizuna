import { apiClient, PaginatedResponse } from '@/shared/api';
import { CreateStoreRequest, Store, StoreStats, UpdateStoreRequest } from '../model/types';

export const platformStoreApi = {
  getList: async (params?: {
    page?: number;
    per_page?: number;
    search?: string;
  }): Promise<PaginatedResponse<Store>> => {
    const response = await apiClient.get('/platform/stores', { params });
    return response.data;
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
