import { PageResult, apiClient, fromSpringPage } from '@/shared/api';
import { MemberPointBalance, MemberPointEntry } from '../model/types';

export const memberPointApi = {
  /** 本人の現在残高を取得する。 */
  balance: async (): Promise<MemberPointBalance> => {
    const response = await apiClient.get('/me/points/balance');
    return response.data;
  },
  /** 本人のポイント明細（全種別）を新しい順に取得する。 */
  entries: async (page: number): Promise<PageResult<MemberPointEntry>> => {
    const response = await apiClient.get('/me/points/entries', { params: { page, size: 20 } });
    return fromSpringPage(response.data);
  },
};
