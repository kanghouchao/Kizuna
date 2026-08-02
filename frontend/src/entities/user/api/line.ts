import { apiClient } from '@/shared/api';
import {
  LineAuthorizationRequest,
  LineConfigResponse,
  LineLoginResponse,
  LineRegisterRequest,
  LoginResponse,
} from '../model/types';

export const platformLineApi = {
  /** LINE ログインの公開設定（匿名）。enabled=false なら入口を描画しない。 */
  config: async (): Promise<LineConfigResponse> => {
    const response = await apiClient.get('/platform/line/config');
    return response.data;
  },
  /**
   * 認可コードを引き換えてログインする（公開・匿名）。
   * 呼び出し元がセッションと失敗表示を自前で扱うため、グローバルな 401 ハンドリングから除外する。
   */
  login: async (data: LineAuthorizationRequest): Promise<LineLoginResponse> => {
    const response = await apiClient.post('/platform/line/login', data, {
      skipAuthRedirect: true,
    } as any);
    return response.data;
  },
  /** 登録チケットで会員を作成し、そのままログインする（公開・匿名）。 */
  register: async (data: LineRegisterRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/platform/line/register', data, {
      skipAuthRedirect: true,
    } as any);
    return response.data;
  },
  /** 既存アカウントへ LINE を連携する。409 は連携済み ないし 当該 LINE が他アカウントに使用済み。 */
  link: async (data: LineAuthorizationRequest): Promise<void> => {
    await apiClient.post('/platform/me/line', data);
  },
};
