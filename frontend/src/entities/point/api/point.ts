import { CursorPageResult, CursorParams, apiClient, fromCursorPage } from '@/shared/api';
import { MemberPointBalance, MemberPointEntry } from '../model/types';

export const memberPointApi = {
  /** 本人の現在残高を取得する。 */
  balance: async (): Promise<MemberPointBalance> => {
    const response = await apiClient.get('/platform/me/points/balance');
    return response.data;
  },
  /** 本人のポイント明細（全種別）を新しい順に取得する。 */
  entries: async (params?: CursorParams): Promise<CursorPageResult<MemberPointEntry>> => {
    const response = await apiClient.get('/platform/me/points/entries', { params });
    return fromCursorPage(response.data);
  },
};
