import { apiClient } from '@/shared/api';
import {
  LoginResponse,
  PasswordChangeRequest,
  LoginRequest,
  MeResponse,
  MeUpdateRequest,
  AuthorizedStore,
} from '../model/types';

export const authApi = {
  login: async (
    credentials: LoginRequest,
    options?: { skipAuthRedirect?: boolean }
  ): Promise<LoginResponse> => {
    // 登録直後のログイン等、呼び出し元が独自にセッションを扱う経路は
    // skipAuthRedirect でグローバルな 401 ハンドリング（token 除去/リダイレクト）から除外する
    const response = await apiClient.post('/auth/login', credentials, {
      skipAuthRedirect: options?.skipAuthRedirect,
    });
    return response.data;
  },
  me: async (): Promise<MeResponse> => {
    const response = await apiClient.get('/me');
    return response.data;
  },
  updateMe: async (data: MeUpdateRequest): Promise<MeResponse> => {
    const response = await apiClient.put('/me', data);
    return response.data;
  },
  stores: async (): Promise<AuthorizedStore[]> => {
    const response = await apiClient.get('/stores/me');
    return response.data;
  },
  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/me/password', data);
  },
  logout: async (): Promise<void> => {
    // 失効済みトークンでの退出は 401 になる（パスワード変更が直前にセッションを畳んだ場合など。
    // 端点は @PermitAll だが Bearer 免除の対象外で、resource-server が controller の手前で弾く）。
    // グローバルな 401 差し戻しに乗せると、退出処理が自分で決めた行き先とログイン画面への
    // 全画面遷移が競合する。会話の畳み方はこの呼び出し元が持っているので除外する。
    await apiClient.post('/auth/logout', undefined, { skipAuthRedirect: true });
  },
};
