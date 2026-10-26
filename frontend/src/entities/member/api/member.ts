import { apiClient } from '@/shared/api';
import {
  MemberHomeResponse,
  MemberRegisterRequest,
  MemberRegisterResponse,
  MemberWithdrawalRequest,
} from '../model/types';

export const memberApi = {
  /** 会員を自助登録する（公開・匿名）。 */
  register: async (data: MemberRegisterRequest): Promise<MemberRegisterResponse> => {
    const response = await apiClient.post('/admin/members', data);
    return response.data;
  },
  /** 本人の会員ポータルホーム（会員コード・表示名）を取得する。 */
  home: async (): Promise<MemberHomeResponse> => {
    const response = await apiClient.get('/me/member');
    return response.data;
  },
  /** 本人が退会する（204）。成功後は旧トークンが失効しているので呼び出し側がログアウトする。 */
  withdraw: async (data: MemberWithdrawalRequest): Promise<void> => {
    await apiClient.post('/me/member/withdrawal', data);
  },
};
