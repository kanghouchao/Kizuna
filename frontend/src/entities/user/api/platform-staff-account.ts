import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import { StaffAccountPasswordResetResponse, StaffAccountSummaryResponse } from '../model/types';

/**
 * アカウント管理 API（本人種別 STAFF の全アカウントの閲覧と停止・再開）。
 * 授権を書く口は持たない — ロールと店舗集合の変更は管理者管理・店舗スタッフ管理の側にある。
 */
export const platformStaffAccountApi = {
  /** 一覧を取得する。page は他一覧と同じ 0 起点（Spring Page 形） */
  list: async (params: {
    page: number;
    size: number;
    search?: string;
  }): Promise<PageResult<StaffAccountSummaryResponse>> => {
    const response = await apiClient.get('/platform/staff-accounts', {
      params: {
        ...toSpringPageParams(params.page, params.size),
        search: params.search,
      },
    });
    return fromSpringPage(response.data);
  },
  /** 停止する。対象のセッションは即時に失効する。 */
  suspend: async (id: number): Promise<void> => {
    await apiClient.post(`/platform/staff-accounts/${id}/suspension`);
  },
  resume: async (id: number): Promise<void> => {
    await apiClient.post(`/platform/staff-accounts/${id}/resumption`);
  },
  /** 仮パスワードを発行して再設定する。生値は戻り値にしか現れず、対象のセッションは即時に失効する。 */
  resetPassword: async (id: number): Promise<StaffAccountPasswordResetResponse> => {
    const response = await apiClient.post(`/platform/staff-accounts/${id}/password-reset`);
    return response.data;
  },
};
