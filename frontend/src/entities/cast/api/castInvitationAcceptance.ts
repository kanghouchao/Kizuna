import { apiClient } from '@/shared/api';
import {
  CastAcceptanceResponse,
  CastInvitationAcceptRequest,
  CastInvitationDetailResponse,
} from '../model/types';

/**
 * 招待受諾の公開 API（/platform/cast-invitations 配下、StoreIdInterceptor を通らない）。
 *
 * トークンはパスではなく本文で送る。パスも問い合わせ文字列もリクエストターゲットとして送られ、
 * リバースプロキシとアプリのアクセスログに 72 時間有効の生値がそのまま残るため、招待が読まれる
 * たびにログへクレデンシャルを書くことになる。照会が GET でないのもこの理由（応答が中間キャッシュ
 * に載る経路も同時に断つ）。
 */
export const castInvitationAcceptanceApi = {
  /** 招待を照会する */
  view: async (token: string): Promise<CastInvitationDetailResponse> => {
    const response = await apiClient.post('/platform/cast-invitations/view', { token });
    return response.data;
  },
  /** 新規登録して招待を受諾する */
  acceptAsNewUser: async (
    token: string,
    data: CastInvitationAcceptRequest
  ): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post('/platform/cast-invitations/acceptance', {
      ...data,
      token,
    });
    return response.data;
  },
  /** 既存アカウント（CAST ロール限定）で招待を受諾する */
  acceptAsExistingUser: async (token: string): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post('/platform/cast-invitations/acceptance/existing', {
      token,
    });
    return response.data;
  },
};
