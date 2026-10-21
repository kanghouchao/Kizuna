import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  PermissionResponse,
  StaffCreateRequest,
  StaffResponse,
  StaffUpdateRequest,
  RoleCreateRequest,
  RoleResponse,
  RoleSummaryResponse,
  RoleUpdateRequest,
  StaffAccountPasswordResetResponse,
} from '../model/types';

export const staffApi = {
  /** スタッフ一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形） */
  list: async (params: {
    page: number;
    size: number;
    search?: string;
    /** 指定店舗を担当範囲に含むスタッフだけへ絞り込む（全店舗担当も該当する） */
    storeId?: number;
  }): Promise<PageResult<StaffResponse>> => {
    const response = await apiClient.get('/admin/staff', {
      params: {
        ...toSpringPageParams(params.page, params.size),
        search: params.search,
        storeId: params.storeId,
      },
    });
    return fromSpringPage(response.data);
  },
  /** 1 件取得。競合後に一覧の現在ページへ居ない対象の最新版を取り直すために使う。 */
  get: async (id: number): Promise<StaffResponse> => {
    const response = await apiClient.get(`/admin/staff/${id}`);
    return response.data;
  },
  create: async (data: StaffCreateRequest): Promise<StaffResponse> => {
    const response = await apiClient.post('/admin/staff', data);
    return response.data;
  },
  update: async (id: number, data: StaffUpdateRequest): Promise<StaffResponse> => {
    const response = await apiClient.put(`/admin/staff/${id}`, data);
    return response.data;
  },
  /** 行使者が授与できるロール（権限が行使者の部分集合のもの）だけを返す。 */
  grantableRoles: async (): Promise<RoleSummaryResponse[]> => {
    const response = await apiClient.get('/admin/staff/grantable-roles');
    return response.data;
  },
  /** 停止する。対象のセッションは即時に失効する。 */
  suspend: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/staff/${id}/suspension`);
  },
  resume: async (id: number): Promise<void> => {
    await apiClient.post(`/admin/staff/${id}/resumption`);
  },
  /** 仮パスワードを発行して再設定する。生値は戻り値にしか現れず、対象のセッションは即時に失効する。 */
  resetPassword: async (id: number): Promise<StaffAccountPasswordResetResponse> => {
    const response = await apiClient.post(`/admin/staff/${id}/password-reset`);
    return response.data;
  },
};

export const roleApi = {
  /** 一覧は権限個数までの要約。編集フォームが要る権限コードの列挙は get で個別に取得する。 */
  list: async (): Promise<RoleSummaryResponse[]> => {
    const response = await apiClient.get('/admin/roles');
    return response.data;
  },
  get: async (id: number): Promise<RoleResponse> => {
    const response = await apiClient.get(`/admin/roles/${id}`);
    return response.data;
  },
  create: async (data: RoleCreateRequest): Promise<RoleResponse> => {
    const response = await apiClient.post('/admin/roles', data);
    return response.data;
  },
  update: async (id: number, data: RoleUpdateRequest): Promise<RoleResponse> => {
    const response = await apiClient.put(`/admin/roles/${id}`, data);
    return response.data;
  },
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/admin/roles/${id}`);
  },
  permissions: async (): Promise<PermissionResponse[]> => {
    const response = await apiClient.get('/admin/permissions');
    return response.data;
  },
};
