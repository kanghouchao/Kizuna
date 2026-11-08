import { apiClient } from '@/shared/api';
import type { DailyRemuneration } from '../model/dailyRemuneration';

export const dailyRemunerationApi = {
  async store(personId: number, businessDate: string, page: number): Promise<DailyRemuneration> {
    return (
      await apiClient.get<DailyRemuneration>('/daily-remunerations', {
        params: { person_id: personId, business_date: businessDate, page, size: 20 },
      })
    ).data;
  },
};
