import { apiClient } from '@/shared/api';
import {
  LoginResponse,
  PasswordChangeRequest,
  PlatformLoginRequest,
  PlatformMeResponse,
  PlatformMeUpdateRequest,
  PlatformStore,
} from '../model/types';

export const platformAuthApi = {
  login: async (
    credentials: PlatformLoginRequest,
    options?: { skipAuthRedirect?: boolean }
  ): Promise<LoginResponse> => {
    // 招待受諾のインラインログイン等、呼び出し元が独自にセッションを扱う経路は
    // skipAuthRedirect でグローバルな 401 ハンドリング（token 除去/リダイレクト）から除外する
    const response = await apiClient.post('/platform/login', credentials, {
      skipAuthRedirect: options?.skipAuthRedirect,
    } as any);
    return response.data;
  },
  me: async (): Promise<PlatformMeResponse> => {
    const response = await apiClient.get('/platform/me');
    return response.data;
  },
  updateMe: async (data: PlatformMeUpdateRequest): Promise<PlatformMeResponse> => {
    const response = await apiClient.put('/platform/me', data);
    return response.data;
  },
  stores: async (): Promise<PlatformStore[]> => {
    const response = await apiClient.get('/platform/stores/me');
    return response.data;
  },
  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/platform/me/password', data);
  },
  logout: async (): Promise<void> => {
    // 失効済みトークンでの退出は 401 になる（パスワード変更が直前にセッションを畳んだ場合など。
    // 端点は @PermitAll だが Bearer 免除の対象外で、resource-server が controller の手前で弾く）。
    // グローバルな 401 差し戻しに乗せると、退出処理が自分で決めた行き先とログイン画面への
    // 全画面遷移が競合する。会話の畳み方はこの呼び出し元が持っているので除外する。
    await apiClient.post('/platform/logout', undefined, { skipAuthRedirect: true } as any);
  },
};
