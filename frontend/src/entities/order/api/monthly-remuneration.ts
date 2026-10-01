import { apiClient, fromSpringPage, Page, PageResult } from '@/shared/api';
import { MonthlyRemuneration, MonthlyRemunerationCast } from '../model/monthlyRemuneration';

const base = '/store/monthly-remunerations';
export const monthlyRemunerationApi = {
  async casts(search: string, page: number): Promise<PageResult<MonthlyRemunerationCast>> {
    return fromSpringPage(
      (
        await apiClient.get<Page<MonthlyRemunerationCast>>(`${base}/casts`, {
          params: { search, page, size: 20 },
        })
      ).data
    );
  },
  async monthly(personId: number, month: string, page: number): Promise<MonthlyRemuneration> {
    return (
      await apiClient.get<MonthlyRemuneration>(base, {
        params: { person_id: personId, month, page, size: 20 },
      })
    ).data;
  },
};
