import { apiClient } from '@/shared/api';
import {
  LoginRequest,
  LoginResponse,
  PasswordChangeRequest,
  RegisterRequest,
  RegisterResponse,
  StoreUserProfileUpdateRequest,
  StoreUserResponse,
} from '../model/types';

export const storeAuthApi = {
  register: async (data: RegisterRequest): Promise<RegisterResponse> => {
    const response = await apiClient.post('/tenant/init-admin-user', data);
    return response.data;
  },
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post('/tenant/login', data);
    return response.data;
  },
  logout: async (): Promise<void> => {
    await apiClient.post('/tenant/logout');
  },
  me: async (): Promise<StoreUserResponse> => {
    const response = await apiClient.get('/tenant/me');
    return response.data;
  },
  updateMe: async (data: StoreUserProfileUpdateRequest): Promise<StoreUserResponse> => {
    const response = await apiClient.put('/tenant/me', data);
    return response.data;
  },
  changePassword: async (data: PasswordChangeRequest): Promise<void> => {
    await apiClient.put('/tenant/password', data);
  },
};
