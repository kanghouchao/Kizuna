import apiClient from '@/lib/client';
import {
  LoginRequest,
  LoginResponse,
  Admin,
  Tenant,
  CreateTenantRequest,
  UpdateTenantRequest,
  TenantStats,
  PaginatedResponse,
  MenuVO,
} from '@/types/api';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/central/login', credentials);
    return response.data;
  },

  me: async (): Promise<Admin> => {
    const response = await apiClient.get('/central/me');
    return response.data;
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/central/logout');
  },
};

export const centralApi = {
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
  getMenus: async (): Promise<MenuVO[]> => {
    const response = await apiClient.get('/central/menus/me');
    return response.data;
  },
};
