import { apiClient, fromSpringPage, PageResult } from '@/shared/api';
import type { SelfMonthlyRemunerationStore } from '../model/selfMonthlyRemuneration';

const base = '/me/monthly-remunerations';

export const selfMonthlyRemunerationApi = {
  async stores(page: number): Promise<PageResult<SelfMonthlyRemunerationStore>> {
    return fromSpringPage(
      (await apiClient.get(`${base}/stores`, { params: { page, size: 20 } })).data
    );
  },
};
