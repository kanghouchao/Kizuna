import type { StoreStatus } from '@/entities/user';
import { apiClient } from '@/shared/api';

/** 呼び出し者の店舗集合の店舗数と稼働状態の内訳。 */
export interface AssignedStores {
  total: number;
  preparing: number;
  active: number;
}

/** 店舗集合の全店の今月の売上。売上の降順、同額は store_id の昇順で、売上 0 の店舗も並ぶ。 */
export interface SalesRanking {
  business_date: string;
  stores: {
    store_id: number;
    store_name: string;
    store_status: StoreStatus;
    sales: number;
    order_count: number;
  }[];
}

export const dashboardApi = {
  getStores: async (): Promise<AssignedStores> =>
    (await apiClient.get<AssignedStores>('/dashboard/stores')).data,
  getSalesRanking: async (): Promise<SalesRanking> =>
    (await apiClient.get<SalesRanking>('/dashboard/sales-ranking')).data,
};
