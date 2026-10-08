import { apiClient } from '@/shared/api';
import type { DailyRemuneration, SelfDailyRemuneration } from '../model/dailyRemuneration';

export const dailyRemunerationApi = {
  async store(personId: number, businessDate: string, page: number): Promise<DailyRemuneration> {
    return (
      await apiClient.get<DailyRemuneration>('/store/daily-remunerations', {
        params: { person_id: personId, business_date: businessDate, page, size: 20 },
      })
    ).data;
  },
  async self(storeId: number, businessDate: string, page: number): Promise<SelfDailyRemuneration> {
    return (
      await apiClient.get<SelfDailyRemuneration>('/platform/me/daily-remunerations', {
        params: { store_id: storeId, business_date: businessDate, page, size: 20 },
      })
    ).data;
  },
};
