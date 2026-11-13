import { apiClient } from '@/shared/api';

/** 呼び出し者の店舗集合の店舗数と稼働状態の内訳。 */
export interface AssignedStores {
  total: number;
  preparing: number;
  active: number;
}

export const dashboardApi = {
  getStores: async (): Promise<AssignedStores> =>
    (await apiClient.get<AssignedStores>('/dashboard/stores')).data,
};
