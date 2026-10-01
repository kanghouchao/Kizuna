import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import {
  SelfMonthlyRemuneration,
  SelfMonthlyRemunerationStore,
} from '../model/selfMonthlyRemuneration';

const base = '/platform/me/monthly-remunerations';

export const selfMonthlyRemunerationApi = {
  async stores(page: number): Promise<PageResult<SelfMonthlyRemunerationStore>> {
    return fromSpringPage(
      (await apiClient.get(`${base}/stores`, { params: { page, size: 20 } })).data
    );
  },
  async monthly(storeId: number, month: string, page: number): Promise<SelfMonthlyRemuneration> {
    return (await apiClient.get(base, { params: { store_id: storeId, month, page, size: 20 } }))
      .data;
  },
};
