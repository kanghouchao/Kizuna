import { apiClient } from '@/shared/api';
import {
  CastAcceptanceResponse,
  CastInvitationAcceptRequest,
  CastInvitationDetailResponse,
  CastInvitationExistingAcceptRequest,
} from '../model/types';

/**
 * 招待受諾の公開 API（/cast-invitations 配下、現在店舗を受けない）。
 *
 * トークンはパスではなく本文で送る。パスも問い合わせ文字列もリクエストターゲットとして送られ、
 * リバースプロキシとアプリのアクセスログに 72 時間有効の生値がそのまま残るため、招待が読まれる
 * たびにログへクレデンシャルを書くことになる。照会が GET でないのもこの理由（応答が中間キャッシュ
 * に載る経路も同時に断つ）。
 */
export const castInvitationAcceptanceApi = {
  /** 招待を照会する */
  view: async (token: string): Promise<CastInvitationDetailResponse> => {
    const response = await apiClient.post('/cast-invitations/view', { token });
    return response.data;
  },
  /** 新規登録して招待を受諾する */
  acceptAsNewUser: async (
    token: string,
    data: CastInvitationAcceptRequest
  ): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post('/cast-invitations/acceptance', {
      ...data,
      token,
    });
    return response.data;
  },
  /**
   * 既存の CAST アカウントの資格情報を添えて招待を受諾する。訪問者が別のアカウントでログイン中でも
   * その Bearer は送られるため、資格情報の誤りの 401 でそのセッションを捨てないよう skipAuthRedirect を付ける
   */
  acceptAsExistingUser: async (
    token: string,
    credentials: CastInvitationExistingAcceptRequest
  ): Promise<CastAcceptanceResponse> => {
    const response = await apiClient.post(
      '/cast-invitations/acceptance/existing',
      { ...credentials, token },
      { skipAuthRedirect: true }
    );
    return response.data;
  },
};
