import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  PermissionResponse,
  PlatformStaffCreateRequest,
  PlatformStaffResponse,
  PlatformStaffUpdateRequest,
  RoleCreateRequest,
  RoleResponse,
  RoleUpdateRequest,
} from '../model/types';

export const platformStaffApi = {
  /** スタッフ一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形） */
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<PlatformStaffResponse>> => {
    const response = await apiClient.get('/platform/staff', {
      params: { ...toSpringPageParams(params.page, params.size), search: params.search },
    });
    return fromSpringPage(response.data);
  },
  create: async (data: PlatformStaffCreateRequest): Promise<PlatformStaffResponse> => {
    const response = await apiClient.post('/platform/staff', data);
    return response.data;
  },
  update: async (id: number, data: PlatformStaffUpdateRequest): Promise<PlatformStaffResponse> => {
    const response = await apiClient.put(`/platform/staff/${id}`, data);
    return response.data;
  },
};

export const platformRoleApi = {
  list: async (): Promise<RoleResponse[]> => {
    const response = await apiClient.get('/platform/roles');
    return response.data;
  },
  create: async (data: RoleCreateRequest): Promise<RoleResponse> => {
    const response = await apiClient.post('/platform/roles', data);
    return response.data;
  },
  update: async (id: number, data: RoleUpdateRequest): Promise<RoleResponse> => {
    const response = await apiClient.put(`/platform/roles/${id}`, data);
    return response.data;
  },
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/platform/roles/${id}`);
  },
  permissions: async (): Promise<PermissionResponse[]> => {
    const response = await apiClient.get('/platform/permissions');
    return response.data;
  },
};
