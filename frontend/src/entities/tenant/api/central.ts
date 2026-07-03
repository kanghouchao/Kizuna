import { apiClient, PaginatedResponse } from '@/shared/api';
import { CreateTenantRequest, Tenant, TenantStats, UpdateTenantRequest } from '../model/types';

export const centralTenantApi = {
  getList: async (params?: {
    page?: number;
    per_page?: number;
    search?: string;
  }): Promise<PaginatedResponse<Tenant>> => {
    const response = await apiClient.get('/central/tenants', { params });
    return response.data;
  },
  getById: async (id: string): Promise<Tenant> => {
    const response = await apiClient.get(`/central/tenant/${id}`);
    return response.data;
  },
  create: async (data: CreateTenantRequest): Promise<Tenant> => {
    const response = await apiClient.post('/central/tenant', data);
    return response.data;
  },
  update: async (id: string, data: UpdateTenantRequest): Promise<Tenant> => {
    const response = await apiClient.put(`/central/tenant/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/central/tenant/${id}`);
  },
  getStats: async (): Promise<TenantStats> => {
    const response = await apiClient.get('/central/tenants/stats');
    return response.data;
  },
};
