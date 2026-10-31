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
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    // 資格情報の誤りはセッションの失効ではない。401 経路（リフレッシュ・差し戻し）に乗せず、呼び出し元が扱う。
    const response = await apiClient.post('/auth/login', credentials, { skipAuthRedirect: true });
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
    // 退出の失敗は 401 経路に乗せない。乗せると、退出処理が自分で決めた行き先とログイン画面への
    // 全画面遷移が競合する。会話の畳み方はこの呼び出し元が持っているので除外する。
    await apiClient.post('/auth/logout', undefined, { skipAuthRedirect: true });
  },
};
