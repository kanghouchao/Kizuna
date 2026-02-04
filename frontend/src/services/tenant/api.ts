import apiClient from '@/lib/client';
import {
  RegisterRequest,
  RegisterResponse,
  LoginRequest,
  LoginResponse,
  MenuVO,
  SiteConfigResponse,
  SiteConfigUpdateRequest,
} from '@/types/api';
import { Order, OrderCreateRequest, Page } from '@/types/order';

export const authApi = {
  register: async (data: RegisterRequest): Promise<RegisterResponse> => {
    const response = await apiClient.post('/tenant/register', data);
    return response.data;
  },
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/tenant/login', data);
    return response.data;
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/tenant/logout');
  },
  me: async (): Promise<any> => {
    const response = await apiClient.get('/tenant/me');
    return response.data;
  },
};

export const tenantApi = {
  getMenus: async (): Promise<MenuVO[]> => {
    const response = await apiClient.get('/tenant/menus/me');
    return response.data;
  },
};

export const orderApi = {
  list: async (params?: any): Promise<Page<Order>> => {
    const response = await apiClient.get('/tenant/orders', { params });
    return response.data;
  },
  create: async (data: OrderCreateRequest): Promise<Order> => {
    const response = await apiClient.post('/tenant/orders', data);
    return response.data;
  },
};

export const siteConfigApi = {
  get: async (): Promise<SiteConfigResponse> => {
    const response = await apiClient.get('/tenant/site-config');
    return response.data;
  },
  update: async (data: SiteConfigUpdateRequest): Promise<SiteConfigResponse> => {
    const response = await apiClient.put('/tenant/site-config', data);
    return response.data;
  },
};
