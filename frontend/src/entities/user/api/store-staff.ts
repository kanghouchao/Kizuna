import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  RoleSummaryResponse,
  StoreStaffCreateRequest,
  StoreStaffResponse,
  StoreStaffUpdateRequest,
} from '../model/types';

/**
 * 店舗コンソールのスタッフ管理。店舗文脈（X-Role / X-Store-ID）は apiClient が /store 宛に自動で載せる。
 * 一覧は文脈店舗で絞られ、対象は店舗側ロールのみを持つアカウントに限られる（HQ 側ロール保持者は在否ごと現れない）。
 */
export const storeStaffApi = {
  /** スタッフ一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形） */
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<StoreStaffResponse>> => {
    const response = await apiClient.get('/store/staff-members', {
      params: {
        ...toSpringPageParams(params.page, params.size),
        search: params.search,
      },
    });
    return fromSpringPage(response.data);
  },
  /** 1 件取得。競合後に一覧の現在ページへ居ない対象の最新版を取り直すために使う。 */
  get: async (id: number): Promise<StoreStaffResponse> => {
    const response = await apiClient.get(`/store/staff-members/${id}`);
    return response.data;
  },
  create: async (data: StoreStaffCreateRequest): Promise<StoreStaffResponse> => {
    const response = await apiClient.post('/store/staff-members', data);
    return response.data;
  },
  update: async (id: number, data: StoreStaffUpdateRequest): Promise<StoreStaffResponse> => {
    const response = await apiClient.put(`/store/staff-members/${id}`, data);
    return response.data;
  },
  /**
   * 行使者が付与できるロールの目録。防提権述語（店舗側ロールか・委譲権限を含むか）はサーバが
   * 判定済みで、前端はここが返した集合をそのまま選択肢にする（判定を複製しない）。
   */
  grantableRoles: async (): Promise<RoleSummaryResponse[]> => {
    const response = await apiClient.get('/store/staff-members/grantable-roles');
    return response.data;
  },
};
