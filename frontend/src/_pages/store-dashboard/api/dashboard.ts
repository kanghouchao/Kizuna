import { apiClient } from '@/shared/api';

/** 営業日が今日の受注の状態別件数。誤完了の無効化を受けた完了は数えない。 */
export interface TodayOrders {
  business_date: string;
  confirmed: number;
  in_service: number;
  completed: number;
  cancelled: number;
}

export const dashboardApi = {
  getTodayOrders: async (): Promise<TodayOrders> =>
    (await apiClient.get<TodayOrders>('/dashboard/today-orders')).data,
};
