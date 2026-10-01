import { apiClient, fromSpringPage, type Page, type PageResult } from '@/shared/api';
import type { MonthlyRemunerationCast } from '../model/monthlyRemuneration';
import type {
  PlatformMonthlyRemuneration,
  PlatformMonthlyRemunerationStore,
} from '../model/platformMonthlyRemuneration';
const base = '/platform/monthly-remunerations';
export const platformMonthlyRemunerationApi = {
  async stores(
    search: string,
    page: number
  ): Promise<PageResult<PlatformMonthlyRemunerationStore>> {
    return fromSpringPage(
      (
        await apiClient.get<Page<PlatformMonthlyRemunerationStore>>(base + '/stores', {
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
  ): Promise<PlatformMonthlyRemuneration> {
    return (
      await apiClient.get<PlatformMonthlyRemuneration>(base, {
        params: { store_id: storeId, person_id: personId, month, page, size: 20 },
      })
    ).data;
  },
};
