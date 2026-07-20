import { apiClient } from '@/shared/api';
import {
  CastAcceptanceResponse,
  CastInvitationAcceptRequest,
  CastInvitationDetailResponse,
} from '../model/types';

/** 招待受諾の公開 API（/platform/cast-invitations 配下、StoreIdInterceptor を通らない）。 */
export const castInvitationAcceptanceApi = {
  /** 招待を照会する */
  view: async (token: string): Promise<CastInvitationDetailResponse> => {
    const response = await apiClient.get(`/platform/cast-invitations/${token}`);
    return response.data;
  },
  /** 新規登録して招待を受諾する */
  acceptAsNewUser: async (
    token: string,
    data: CastInvitationAcceptRequest
  ): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post(`/platform/cast-invitations/${token}/acceptance`, data);
    return response.data;
  },
  /** 既存アカウント（CAST ロール限定）で招待を受諾する */
  acceptAsExistingUser: async (token: string): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post(
      `/platform/cast-invitations/${token}/acceptance/existing`
    );
    return response.data;
  },
};
