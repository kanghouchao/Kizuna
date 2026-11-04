import { apiClient } from '@/shared/api';
import {
  MemberHomeResponse,
  MemberNotificationPurpose,
  MemberNotificationSettings,
  MemberRegisterRequest,
  MemberRegisterResponse,
  MemberWithdrawalRequest,
} from '../model/types';

export const memberApi = {
  /** 会員を自助登録する（公開・匿名）。 */
  register: async (data: MemberRegisterRequest): Promise<MemberRegisterResponse> => {
    const response = await apiClient.post('/members', data);
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
  /** 本人の通知設定（用途ごとの実効値）を取得する。 */
  notificationSettings: async (): Promise<MemberNotificationSettings> => {
    const response = await apiClient.get('/me/member/notification-settings');
    return response.data;
  },
  /** 一つの用途を許可か拒否に決め、変更後の設定全体を返す。 */
  chooseNotification: async (
    purpose: MemberNotificationPurpose,
    status: 'ALLOWED' | 'DENIED'
  ): Promise<MemberNotificationSettings> => {
    const response = await apiClient.put(`/me/member/notification-settings/${purpose}`, {
      status,
    });
    return response.data;
  },
};
