import { apiClient, PageResult, fromSpringPage, toSpringPageParams } from '@/shared/api';
import {
  StoreManagerAppointRequest,
  StoreManagerCandidateResponse,
  StoreManagerResponse,
} from '../model/types';

/**
 * 店舗管理ページの店長設定。店長は独立した記録ではなく「STORE_MANAGER 保持かつ当該店舗を担当」の
 * 導出なので、任命は集合への追加（POST）、解任は集合からの除去（DELETE）で表される。
 * 降格だけは店舗集合でなくロールの側を書き換えるので、名詞化した子リソースへの POST で表す。
 */
export const storeManagerApi = {
  /** この店舗の店長一覧。1 店舗の店長は有界なので分頁しない。 */
  list: async (storeId: number | string): Promise<StoreManagerResponse[]> => {
    const response = await apiClient.get(`/platform/stores/${storeId}/managers`);
    return response.data;
  },
  /** 任命できる既存アカウントの候補。母集団はサーバが絞る（前端は「任命できるとは何か」を持たない）。 */
  candidates: async (
    storeId: number | string,
    params: { page: number; size: number; search?: string }
  ): Promise<PageResult<StoreManagerCandidateResponse>> => {
    const response = await apiClient.get(`/platform/stores/${storeId}/manager-candidates`, {
      params: {
        ...toSpringPageParams(params.page, params.size),
        search: params.search,
      },
    });
    return fromSpringPage(response.data);
  },
  appoint: async (
    storeId: number | string,
    data: StoreManagerAppointRequest
  ): Promise<StoreManagerResponse> => {
    const response = await apiClient.post(`/platform/stores/${storeId}/managers`, data);
    return response.data;
  },
  dismiss: async (storeId: number | string, userId: number): Promise<void> => {
    await apiClient.delete(`/platform/stores/${storeId}/managers/${userId}`);
  },
  /** 降格。担当店舗はそのままに、ロールを店長から店舗スタッフへ入れ替える。 */
  demote: async (storeId: number | string, userId: number): Promise<void> => {
    await apiClient.post(`/platform/stores/${storeId}/managers/${userId}/demotion`);
  },
};
