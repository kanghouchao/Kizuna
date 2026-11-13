import { apiClient } from '@/shared/api';

/** 営業日が今日の受注の状態別件数。誤完了の無効化を受けた完了は数えない。 */
export interface TodayOrders {
  business_date: string;
  confirmed: number;
  in_service: number;
  completed: number;
  cancelled: number;
}

/** 希望営業日が今日以降の未処理の予約申請の件数。希望日を過ぎた申請は確定も謝絶もできないので数えない。 */
export interface PendingOrderApplications {
  business_date: string;
  count: number;
}

/** 未処理の件数だけを返す区画の応答。 */
export interface PendingCount {
  count: number;
}

export const dashboardApi = {
  getTodayOrders: async (): Promise<TodayOrders> =>
    (await apiClient.get<TodayOrders>('/dashboard/today-orders')).data,
  getPendingOrderApplications: async (): Promise<PendingOrderApplications> =>
    (await apiClient.get<PendingOrderApplications>('/dashboard/pending-order-applications')).data,
  getPendingShiftRequests: async (): Promise<PendingCount> =>
    (await apiClient.get<PendingCount>('/dashboard/pending-shift-requests')).data,
  getPendingReviews: async (): Promise<PendingCount> =>
    (await apiClient.get<PendingCount>('/dashboard/pending-reviews')).data,
};
