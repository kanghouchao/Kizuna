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
    // skipAuthRedirect でグローバルな 401 ハンドリング（token 除去/リダイレクト）から除外する（#327 codex指摘）
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
    const response = await apiClient.get('/platform/stores');
    return response.data;
  },
  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/platform/password', data);
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/platform/logout');
  },
};
