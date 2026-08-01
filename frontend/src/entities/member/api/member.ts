import { apiClient } from '@/shared/api';
import { MemberHomeResponse, MemberRegisterRequest, MemberRegisterResponse } from '../model/types';

export const memberApi = {
  /** 会員を自助登録する（公開・匿名）。 */
  register: async (data: MemberRegisterRequest): Promise<MemberRegisterResponse> => {
    const response = await apiClient.post('/platform/members', data);
    return response.data;
  },
  /** 本人の会員ポータルホーム（会員コード・表示名）を取得する。 */
  home: async (): Promise<MemberHomeResponse> => {
    const response = await apiClient.get('/platform/me/member');
    return response.data;
  },
};
