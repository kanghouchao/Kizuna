import { CursorPageResult, CursorParams, apiClient, fromCursorPage } from '@/shared/api';
import {
  EmergencyElevationActivationRequest,
  EmergencyElevationActivationResponse,
  EmergencyElevationSummary,
} from '../model/types';

export const emergencyElevationApi = {
  /** 緊急昇格を発動する。応答の token は昇格トークンの生値で、この応答にしか現れない。 */
  activate: async (
    data: EmergencyElevationActivationRequest
  ): Promise<EmergencyElevationActivationResponse> => {
    // 再認証の失敗はログインと同じ 401 で返る。グローバルな 401 ハンドリングに乗せると
    // パスワードの打ち間違いで有効なセッションごと破棄されるため、フォーム側で提示する
    const response = await apiClient.post('/platform/emergency-elevations', data, {
      skipAuthRedirect: true,
    } as any);
    return response.data;
  },
  /** 発動履歴を新しい順に取得する。続きは応答の nextCursor をそのまま cursor に渡して取る。 */
  list: async (params?: CursorParams): Promise<CursorPageResult<EmergencyElevationSummary>> => {
    const response = await apiClient.get('/platform/emergency-elevations', { params });
    return fromCursorPage(response.data);
  },
  /** 発動を撤回する。発動者の全セッション（昇格トークンと通常トークンの双方）が失効する。 */
  revoke: async (elevationId: number): Promise<void> => {
    await apiClient.post(`/platform/emergency-elevations/${elevationId}/revocation`);
  },
};
