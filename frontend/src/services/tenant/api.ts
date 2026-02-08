import apiClient from '@/lib/client';
import {
  RegisterRequest,
  RegisterResponse,
  LoginRequest,
  LoginResponse,
  MenuVO,
  TenantConfigResponse,
  TenantConfigUpdateRequest,
  FileUploadResponse,
  CastResponse,
  CastCreateRequest,
  CastUpdateRequest,
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

export const tenantConfigApi = {
  get: async (): Promise<TenantConfigResponse> => {
    const response = await apiClient.get('/tenant/config');
    return response.data;
  },
  update: async (data: TenantConfigUpdateRequest): Promise<TenantConfigResponse> => {
    const response = await apiClient.put('/tenant/config', data);
    return response.data;
  },
};

export const fileApi = {
  /** ファイルをアップロードする */
  upload: async (file: File, directory: string = 'general'): Promise<FileUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('directory', directory);
    const response = await apiClient.post('/tenant/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};

export const castApi = {
  /** キャスト一覧を取得する */
  list: async (params?: any): Promise<any> => {
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
};
