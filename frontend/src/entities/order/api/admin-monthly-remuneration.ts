import { apiClient, fromSpringPage, type Page, type PageResult } from '@/shared/api';
import type { MonthlyRemunerationCast } from '../model/monthlyRemuneration';
import type {
  AdminMonthlyRemuneration,
  AdminMonthlyRemunerationStore,
} from '../model/adminMonthlyRemuneration';
const base = '/admin/monthly-remunerations';
export const adminMonthlyRemunerationApi = {
  async stores(search: string, page: number): Promise<PageResult<AdminMonthlyRemunerationStore>> {
    return fromSpringPage(
      (
        await apiClient.get<Page<AdminMonthlyRemunerationStore>>(base + '/stores', {
          params: { search, page, size: 20 },
        })
      ).data
    );
  },
  async casts(
    storeId: number,
    search: string,
    page: number
  ): Promise<PageResult<MonthlyRemunerationCast>> {
    return fromSpringPage(
      (
        await apiClient.get<Page<MonthlyRemunerationCast>>(base + '/casts', {
          params: { store_id: storeId, search, page, size: 20 },
        })
      ).data
    );
  },
  async monthly(
    storeId: number,
    personId: number,
    month: string,
    page: number
  ): Promise<AdminMonthlyRemuneration> {
    return (
      await apiClient.get<AdminMonthlyRemuneration>(base, {
        params: { store_id: storeId, person_id: personId, month, page, size: 20 },
      })
    ).data;
  },
};
